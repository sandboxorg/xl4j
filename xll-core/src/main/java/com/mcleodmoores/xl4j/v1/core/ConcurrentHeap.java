/**
 * Copyright (C) 2014 - Present McLeod Moores Software Limited.  All rights reserved.
 */
package com.mcleodmoores.xl4j.v1.core;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mcleodmoores.xl4j.v1.api.core.Heap;
import com.mcleodmoores.xl4j.v1.util.ConcurrentIdentityHashMap;
import com.mcleodmoores.xl4j.v1.util.XL4JRuntimeException;

/**
 * Class to store objects and allocate handles for objects.
 */
public class ConcurrentHeap implements Heap {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentHeap.class);

  private static final int MILLIS_PER_SECOND = 1000;
  private static final int BYTES_IN_64BITS = 8;
  private static final int MAX_COLLECTION_COUNT = 3;
  private final ConcurrentHashMap<Long, Object> _handleToObj;
  private final ConcurrentIdentityHashMap<Object, Long> _objToHandle;
  private final ConcurrentHashMap<Long, Integer> _handleToCollectCount;
  private final AtomicLong _sequence;
  private long _snapHandle;

  // TODO: Need some sort of check-pointing as current GC won't work without freezing sheet operations. #44
  /**
   * Construct a heap.
   */
  public ConcurrentHeap() {
    _handleToObj = new ConcurrentHashMap<>();
    _objToHandle = new ConcurrentIdentityHashMap<>();
    _handleToCollectCount = new ConcurrentHashMap<>();
    // we try and create the handle counter by combining the local MAC, the time and the sheet id.
    // this should minimize the possibility of stale handles in sheets being interpreted as valid.
    long baseHandle;
    Enumeration<NetworkInterface> networkInterfaces;
    try {
      networkInterfaces = NetworkInterface.getNetworkInterfaces();
      if (networkInterfaces.hasMoreElements()) {
        final NetworkInterface networkInterface = networkInterfaces.nextElement();
        final byte[] hardwareAddress = networkInterface.getHardwareAddress();
        final byte[] extendedTo64bits = new byte[BYTES_IN_64BITS];
        if (hardwareAddress == null) {
          baseHandle = new SecureRandom().nextLong();
        } else {
          // we assume the hardware address is going to be 6 bytes, but we handle if it isn't, but top out at 8 bytes
          System.arraycopy(hardwareAddress, 0, extendedTo64bits, 0, Math.min(hardwareAddress.length, BYTES_IN_64BITS));
          final ByteBuffer byteBuffer = ByteBuffer.wrap(extendedTo64bits);
          baseHandle = byteBuffer.getLong();
        }
      } else {
        baseHandle = new SecureRandom().nextLong();
      }
    } catch (final SocketException e) {
      baseHandle = new SecureRandom().nextLong();
    }
    baseHandle += System.currentTimeMillis() / MILLIS_PER_SECOND; // we only need seconds.
    _sequence = new AtomicLong(baseHandle);
  }

  /* (non-Javadoc)
   * @see com.mcleodmoores.xl4j.v1.core.Heap#getHandle(java.lang.Object)
   */
  @Override
  public long getHandle(final Object object) {
    final Long key = _objToHandle.get(object);
    if (key == null) {
      synchronized (object) { // should be low contention at least, can we get rid of this lock?
        // check no one snuck in while we were waiting with the same object.
        final Long keyAgain = _objToHandle.get(object);
        if (keyAgain != null) {
          return keyAgain;
        }
        final long newKey = _sequence.getAndIncrement();
        // we don't need locking here because no one has the key yet.
        // theoretically the user passing getHandle could concurrently call it twice with the same object, but
        // that's why we synchronize on object and re-check once we have the lock.
        LOGGER.trace("Creating new object handle " + Long.toUnsignedString(newKey));
        _handleToObj.put(newKey, object);
        _objToHandle.put(object, newKey);
        return newKey;
      }
    } else {
      return key;
    }
  }

  /* (non-Javadoc)
   * @see com.mcleodmoores.xl4j.v1.core.Heap#getObject(long)
   */
  @Override
  public Object getObject(final long handle) {
    final Object object = _handleToObj.get(handle);
    if (object == null) {
      LOGGER.warn("Cannot find object with handle " + handle);
      throw new XL4JRuntimeException("Cannot find object with handle " + handle);
    } else {
      return object;
    }
  }

  /**
   * Start a garbage collection pass.
   */
  private void startGC() {
    _snapHandle = _sequence.get();
    LOGGER.trace("GC starting, snapping to " + Long.toUnsignedString(_snapHandle));
  }

  /**
   * Remove any objects that aren't live, minimizing locking period.
   */
  private long endGC(final long[] activeHandles) {
    Arrays.sort(activeHandles);
    long unique = 0;
    for (int i = 0; i < activeHandles.length; i++) {
      if (i < activeHandles.length - 1) {
        if (activeHandles[i + 1] != activeHandles[i]) {
          unique++;
        }
      } else {
        unique++;
      }
      LOGGER.trace("handle[{}] = {}", i, Long.toUnsignedString(activeHandles[i]));
    }
    final Iterator<Entry<Long, Object>> iterator = _handleToObj.entrySet().iterator();
    long removed = 0;
    long currentHandles = 0;
    while (iterator.hasNext()) {
      final Entry<Long, Object> next = iterator.next();
      if (next.getKey() >= _snapHandle) {
        LOGGER.trace("Handle {} >= snap {} so skipping", Long.toUnsignedString(next.getKey()), Long.toUnsignedString(_snapHandle));
        continue; // skip as we might have missed it in our scan because it was created after we started
      }
      if (Arrays.binarySearch(activeHandles, next.getKey()) < 0) { 
        // if this key isn't in the active handles list it's a candidate for collection 
        final Integer count = _handleToCollectCount.get(next.getKey());
        if (count == null) {
          // we start counting how many times this has been flagged for collection
          _handleToCollectCount.put(next.getKey(), 1);
          LOGGER.trace("Started count for handle " + Long.toUnsignedString(next.getKey()) + " at 1");
        } else if (count < MAX_COLLECTION_COUNT) {
          LOGGER.trace("Increasing count for handle " + Long.toUnsignedString(next.getKey()) + " to " + count + 1);
          _handleToCollectCount.put(next.getKey(), count + 1);
        } else {
          LOGGER.trace("Count for handle " + Long.toUnsignedString(next.getKey()) + " reached limit so removing");
          _handleToCollectCount.remove(next.getKey());
          iterator.remove(); // didn't find handle, meaning it's not active, gc.
          _objToHandle.remove(next.getValue());
          removed++;
        }
      } else {
        // keep track of how many of the items in the heap _were_ in the active handles list so we can establish 
        currentHandles++;
      }
    }
    LOGGER.trace(removed + " objects removed during GC pass");
    return unique - currentHandles;
  }

  /* (non-Javadoc)
   * @see com.mcleodmoores.xl4j.v1.core.Heap#cycleGC(long[])
   */
  @Override
  public long cycleGC(final long[] activeHandles) {
    //final long snapBefore = _snapHandle;
    long unrecognisedHandles = endGC(activeHandles);
    LOGGER.trace("There were " + unrecognisedHandles + " unrecognised handles");
    if (unrecognisedHandles > 0) {
      LOGGER.error("There were unrecognised handles, triggering recalc");
    }
    startGC();
    return unrecognisedHandles; //_snapHandle - snapBefore; // object allocated during cycle
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("WorksheetHeap[\n");
    for (final Entry<Long, Object> entry : _handleToObj.entrySet()) {
      final String number = Long.toUnsignedString(entry.getKey());
      sb.append("  ");
      sb.append(number);
      sb.append(" = > ");
      sb.append(entry.getValue().toString());
      sb.append('\n');
    }
    sb.append(']');
    return sb.toString();
  }
}
