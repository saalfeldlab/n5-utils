package org.janelia.saalfeldlab.grid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import gnu.trove.impl.Constants;
import gnu.trove.set.hash.TLongHashSet;
import ij.ImageJ;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.converter.Converters;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.array.LongArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.position.FunctionRandomAccessible;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineRealRandomAccessible;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.util.Fraction;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.view.Views;


public class LongListTypeTest {

	private static final long[] expectedStorage = new long[100];
	private static final long[][] expectedValues = new long[10][8];

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {

		final Random rnd = new Random(0);
		for (int k =0, i = 0; i < expectedValues.length; ++i) {
			for (int j = 0; j < expectedValues[i].length; ++j, ++k) {
				final long value = rnd.nextLong();
				expectedStorage[k] = value;
				expectedValues[i][j] = value;
			}
			expectedStorage[k++] = expectedValues[i].length;
		}
	}

	@Test
	public void testVariable() {

		for (int i = 0; i < expectedValues.length; ++i) {

			final LongListType t = new LongListType(expectedValues[i].length);

			for (int j = 0; j < expectedValues[i].length; ++j) {
				t.add(expectedValues[i][j]);
			}

			final long[] actuals = new long[expectedValues[i].length];
			t.read(actuals);

			Assert.assertArrayEquals(expectedValues[i], actuals);
		}
	}

	@Test
	public void testStorage() {

//		final ArrayImg<LongListType, ?> img =
//				new ArrayImgFactory<LongListType>(
//						new LongListType(expectedValues[0].length))
//				.create(expectedValues.length);

		final LongListType type = new LongListType(expectedValues[0].length);
		final Fraction entitiesPerPixel = type.getEntitiesPerPixel();
		final LongArray data = new LongArray(expectedStorage);
		final ArrayImg<LongListType, LongArray> img = new ArrayImg<>(
				data,
				new long[] {expectedValues.length},
				entitiesPerPixel);
		img.setLinkedType(type.getNativeTypeFactory().createLinkedType(img));

		int i = 0;
		final long[] actuals = new long[expectedValues[i].length];
		for (final LongListType t : img) {

			t.read(actuals);

			Assert.assertArrayEquals(expectedValues[i], actuals);
			++i;
		}
	}

	@Test
	public void testAdd() {

		for (int i = 0; i < expectedValues.length; ++i) {

			final int m = expectedValues[i].length / 2 + 1;
			final LongListType a = new LongListType(expectedValues[i].length);
			final LongListType b = new LongListType(expectedValues[i].length);

			for (int j = 0; j < m; ++j)
				a.add(expectedValues[i][j]);
			for (int j = m; j < expectedValues[i].length; ++j) {
				b.add(expectedValues[i][j]);
			}
			a.add(b);

			final long[] actuals = new long[expectedValues[i].length];
			a.read(actuals);

			Assert.assertArrayEquals(expectedValues[i], actuals);
		}
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testRendering() throws InterruptedException {

		final FinalInterval interval = new FinalInterval(10, 20, 60);

		final int capacity = 1 << interval.numDimensions();

		final FunctionRandomAccessible<LongListType> ids = new FunctionRandomAccessible<>(
				interval.numDimensions(),
				(x, t) -> t.set(IntervalIndexer.positionToIndex(x, interval), 0),
				() -> {
					final LongListType t = new LongListType(capacity);
					t.add(-1);
					return t;
				});

		final LongListType tOut = new LongListType(interval.numDimensions());
		tOut.add(-1);
		Views.extendValue(
				Views.interval(ids, interval),
				tOut);

		new ImageJ();

		/* test generated ids */
		final RandomAccessibleInterval<LongListType> idInterval = Views.interval(ids, interval);
		ImageJFunctions.show(Views.stack(decompose(idInterval)));

		/* test interpolation */
		final RealRandomAccessible<LongListType> interpolatedIds = Views.interpolate(
				ids,
				new NLinearInterpolatorFactory<>());

		final RandomAccessibleInterval<LongListType> interpolatedIdInterval =
				Views.interval(
						Views.raster(interpolatedIds),
						interval);
		ImageJFunctions.show(Views.stack(decompose(interpolatedIdInterval)));

		/* test real block sizes */
		final int[] blockSize = {40, 30, 20};
		final AffineTransform3D affine = new AffineTransform3D();
		affine.scale(blockSize[0], blockSize[1], blockSize[2]);
		affine.translate(-0.5, -0.5, -0.5);

		final AffineRealRandomAccessible<LongListType, AffineGet> transformedInterpolatedIds =
				RealViews.affineReal(
						interpolatedIds,
						affine);
		final FinalInterval scaledInterval =
				new FinalInterval(
						interval.dimension(0) * blockSize[0],
						interval.dimension(1) * blockSize[1],
						interval.dimension(2) * blockSize[2]);
		final RandomAccessibleInterval<LongListType> transformedInterpolatedIdInterval =
				Views.interval(
						Views.raster(transformedInterpolatedIds),
						scaledInterval);
		ImageJFunctions.show(Views.stack(decompose(transformedInterpolatedIdInterval)));

		/* rotate and scale */
		final AffineTransform3D similarity = new AffineTransform3D();
		similarity.translate(
				-0.5 * transformedInterpolatedIdInterval.dimension(0),
				-0.5 * transformedInterpolatedIdInterval.dimension(1),
				-0.5 * transformedInterpolatedIdInterval.dimension(2));
		similarity.rotate(0, Math.PI / 10);
		similarity.rotate(2, Math.PI / 20);
		similarity.translate(
				0.5 * transformedInterpolatedIdInterval.dimension(0),
				0.5 * transformedInterpolatedIdInterval.dimension(1),
				0.5 * transformedInterpolatedIdInterval.dimension(2));
		similarity.scale(0.5);
		similarity.concatenate(affine);

		final double blockScale = 1.0 / Arrays.stream(blockSize).min().getAsInt();
		final AffineTransform3D screen = new AffineTransform3D();
		screen.scale(blockScale);
		screen.concatenate(similarity);

		final AffineRealRandomAccessible<LongListType, AffineGet> viewerInterpolatedIds =
				RealViews.affineReal(
						interpolatedIds,
						screen);

		final int screenWidth = 1600;
		final int screenHeight = 900;
		final FinalInterval viewerInterval =
				new FinalInterval(
						(long)Math.ceil(screenWidth * blockScale),
						(long)Math.ceil(screenHeight * blockScale));

		final RandomAccessibleInterval<LongListType> viewerInterpolatedIdInterval =
				Views.interval(
						Views.hyperSlice(
								Views.raster(viewerInterpolatedIds),
								2,
								0),
						viewerInterval);

		ImageJFunctions.show(Views.stack(decompose(viewerInterpolatedIdInterval)));

		/* scan the contents of a view scales */
		for (double scanBlockScale = 1; scanBlockScale > 0.01; scanBlockScale *= 0.9) {

			final AffineTransform3D scanScreen = new AffineTransform3D();
			scanScreen.scale(scanBlockScale);
			scanScreen.concatenate(similarity);

			final AffineRealRandomAccessible<LongListType, AffineGet> scanViewerInterpolatedIds =
					RealViews.affineReal(
							interpolatedIds,
							scanScreen);

			final int scanScreenWidth = 1600;
			final int scanScreenHeight = 900;
			final FinalInterval scanViewerInterval =
					new FinalInterval(
							(long)Math.ceil(scanScreenWidth * scanBlockScale),
							(long)Math.ceil(scanScreenHeight * scanBlockScale));

			final RandomAccessibleInterval<LongListType> scanViewerInterpolatedIdInterval =
					Views.interval(
							Views.hyperSlice(
									Views.raster(scanViewerInterpolatedIds),
									2,
									0),
							scanViewerInterval);

			final TLongHashSet unique = new TLongHashSet(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
			for (final LongListType t : Views.iterable(scanViewerInterpolatedIdInterval)) {
				t.read(unique);
			}

			System.out.println(scanBlockScale + " : " + unique.size());
		}


		Thread.sleep(100000);
	}

	private static List<RandomAccessibleInterval<LongType>> decompose(
			final RandomAccessibleInterval<LongListType> src) {

		final long capacity = src.randomAccess().get().getCapacity();
		final ArrayList<RandomAccessibleInterval<LongType>> list = new ArrayList<>();
		for (int i = 0; i < capacity; ++i) {
			final int fi = i;
			list.add(Converters.convert(
					src,
					(a, b) -> b.setLong(a.get(fi)),
					new LongType()));
		}
		return list;
	}
}
