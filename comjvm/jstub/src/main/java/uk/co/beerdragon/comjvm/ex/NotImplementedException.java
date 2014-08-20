/*
 * COM Java wrapper 
 *
 * Copyright 2014 by Andrew Ian William Griffin <griffin@beerdragon.co.uk>.
 * Released under the GNU General Public License.
 */
package uk.co.beerdragon.comjvm.ex;

/**
 * Specialisation of {@code COMException} for {@code E_NOTIMPL}.
 */
public final class NotImplementedException extends COMException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates a new instance.
   * <p>
   * Instances are normally created by {@link COMException#of} rather than calling this constructor
   * directly.
   */
  public NotImplementedException () {
    super (HRESULT.E_NOTIMPL);
  }

  // COMException

  @Override
  public String toString () {
    return "E_NOTIMPL";
  }

}