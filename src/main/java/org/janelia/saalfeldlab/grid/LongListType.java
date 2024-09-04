/**
 *
 */
package org.janelia.saalfeldlab.grid;

import gnu.trove.set.TLongSet;
import net.imglib2.img.NativeImg;
import net.imglib2.img.basictypeaccess.LongAccess;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.type.AbstractNativeType;
import net.imglib2.type.NativeTypeFactory;
import net.imglib2.type.operators.Add;
import net.imglib2.type.operators.MulFloatingPoint;
import net.imglib2.util.Fraction;

/**
 * @author Stephan Saalfeld &lt;saalfelds@janelia.hhmi.org&gt;
 *
 */
public class LongListType extends AbstractNativeType<LongListType> implements Add<LongListType>, MulFloatingPoint {

	private final int capacity;
	private final int stepSize;

	final protected NativeImg< ?, ? extends LongAccess > img;

	// the DataAccess that holds the information
	protected LongAccess dataAccess;

	private final NativeTypeFactory<LongListType, LongAccess> typeFactory;

	// this is the constructor if you want it to read from an array
	public LongListType(final NativeImg<?, ? extends LongAccess> longStorage, final int numElements) {

		super();
		img = longStorage;
		this.capacity = numElements;
		stepSize = numElements + 1;
		typeFactory = NativeTypeFactory.LONG((img) -> new LongListType(img, numElements));
	}

	public LongListType(final int numElements) {

		super();
		img = null;
		this.capacity = numElements;
		stepSize = numElements + 1;
		dataAccess = new LongArray(stepSize);
		typeFactory = NativeTypeFactory.LONG((img) -> new LongListType(img, numElements));
	}

	// this is the constructor if you want to specify the dataAccess
	public LongListType(final LongAccess access, final int numElements) {

		super();
		img = null;
		dataAccess = access;
		this.capacity = numElements;
		stepSize = numElements + 1;
		typeFactory = NativeTypeFactory.LONG((img) -> new LongListType(img, numElements));
	}

	public long getCapacity() {

		return capacity;
	}

	@Override
	public Fraction getEntitiesPerPixel() {

		return new Fraction(stepSize, 1);
	}

	@Override
	public LongListType duplicateTypeOnSameNativeImg() {

		return new LongListType(img, capacity);
	}

	@Override
	public NativeTypeFactory<LongListType, LongAccess> getNativeTypeFactory() {

		return typeFactory;
	}

	@Override
	public void updateContainer(final Object c) {

		dataAccess = img.update(c);
	}

	@Override
	public LongListType createVariable() {

		return new LongListType(capacity);
	}

	@Override
	public LongListType copy() {

		final LongListType copy = new LongListType(capacity);
		copy.set(this);
		return copy;
	}

	public long get(final int j) {

		final int ai = i.get() * stepSize;
		return dataAccess.getValue(ai + j);
	}

	/**
	 * Fill an array with the elements of this {@link LongListType}.
	 * This is more efficient than subsequent calls of {@link #get(int)}
	 * because the base offset has to be calculated only once.
	 *
	 * Overwrites only elements that have been accumulated, so you have
	 * to keep the array clean, i.e. filled with -1 or something
	 * indicative of an element being unused if that is important.
	 *
	 * @param values
	 */
	public void read(final long[] values) {

		final int ai = i.get() * stepSize;
		final long n = dataAccess.getValue(ai + capacity);
		for (int j = 0; j < n; ++j)
			values[j] = dataAccess.getValue(ai + j);
	}

	/**
	 * Read the elements of this {@link LongListType} in a {@link TLongSet}.
	 * This is more efficient than subsequent calls of {@link #get(int)}
	 * because the base offset has to be calculated only once.
	 *
	 * @param set
	 */
	public void read(final TLongSet set) {

		final int ai = i.get() * stepSize;
		final long n = dataAccess.getValue(ai + capacity);
		for (int j = 0; j < n; ++j)
			set.add(dataAccess.getValue(ai + j));
	}

	/**
	 * Set all accumulated values and the next index value to those of
	 * another {@link LongListType}.  Behavior is undefined if the other
	 * {@link LongListType} has a different number of elements, this is
	 * not checked.
	 */
	@Override
	public void set(final LongListType t) {

		final int ai = i.get() * stepSize;
		final int ti = t.i.get() * stepSize;

		final long n = t.dataAccess.getValue(ti + capacity);
		dataAccess.setValue(ai + capacity, n);

		for (int j = 0; j < n; ++j)
			dataAccess.setValue(ai + j, t.dataAccess.getValue(ti + j));
	}

	/**
	 * Modify the value at a specified index.  This method does not update the
	 * capacity.  Use {@link #add(long)} to add values.
	 *
	 * @param value
	 * @param index
	 */
	public void set(final long value, final int index) {

		final int ai = i.get() * stepSize;
		dataAccess.setValue(ai + index, value);
	}

	/**
	 * Set the size.  The contents of this {@link LongListType} are not
	 * changed, i.e. increasing the capacity may make undefined data visible
	 * and decreasing it will not clear previously available data but make it
	 * available for overwriting and would not use it for value returns or
	 * comparisons.
	 *
	 * @param size
	 */
	public void setSize(final int size) {

		final int ai = i.get() * stepSize;
		dataAccess.setValue(ai + capacity, size);
	}

	/**
	 * Checks only equality of values up to the current fill status because
	 * the underlying {@link LongAccess} may contain some left overs that
	 * we don't care about.
	 */
	@Override
	public boolean valueEquals(final LongListType t) {

		if (capacity != t.capacity)
			return false;

		final int ai = i.get() * stepSize;
		final int ti = t.i.get() * stepSize;

		final long na = dataAccess.getValue(ai + capacity);
		final long nt = t.dataAccess.getValue(ti + capacity);

		if (na != nt)
			return false;

		for (int j = 0; j < na; ++j)
			if (dataAccess.getValue(ai + j) != t.dataAccess.getValue(ti + j))
				return false;
		return true;
	}

	public void add(final long value) {

		final int ai = i.get() * stepSize;
		final int ni = ai + capacity;
		final int n = (int)dataAccess.getValue(ni);
		dataAccess.setValue(ai + n, value);
		dataAccess.setValue(ni, n + 1);
	}

	@Override
	public String toString() {

		final int ai = i.get() * stepSize;
		final long n = dataAccess.getValue(ai + capacity);
		final StringBuilder str = new StringBuilder("[");
		for (int j = 0; j < n; ++j) {
			if (j > 0)
				str.append(",");
			str.append(dataAccess.getValue(ai + j));
		}
		str.append("]");
		return str.toString();
	}

	/**
	 * Non functional facade to re-use existing n-linear interpolation.
	 */
	@Override
	public void mul(final float t) {}

	/**
	 * Non functional facade to re-use existing n-linear interpolation.
	 */
	@Override
	public void mul(final double t) {}

	@Override
	public void add(final LongListType t) {

		final int ai = i.get() * stepSize;
		final int ti = t.i.get() * stepSize;

		final int nai = ai + capacity;
		final int na = (int)dataAccess.getValue(nai);
		final int nt = (int)t.dataAccess.getValue(ti + t.capacity);

		for (int j = 0; j < nt; ++j)
			dataAccess.setValue(
					ai + na + j,
					t.dataAccess.getValue(ti + j));

		dataAccess.setValue(nai, na + nt);
	}
}
