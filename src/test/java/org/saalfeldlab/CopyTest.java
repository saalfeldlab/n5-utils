package org.saalfeldlab;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Random;

import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Writer;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.type.Type;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;

public class CopyTest {

	private static String testDirPath;

	private static final long[] dimensions = new long[]{100, 200, 300};
	private static final int[] blockSize = new int[]{44, 33, 22};
	private static byte[] bytes;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {

		testDirPath = Files.createTempDirectory("n5").toFile().getPath() + "/n5-utils-test.hdf5";

		final Random rnd = new Random();
		bytes = new byte[(int)dimensions[0] * (int)dimensions[1] * (int)dimensions[2]];
		rnd.nextBytes(bytes);
		Files.createDirectories(Paths.get(testDirPath).getParent());
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {

		Files.delete(Paths.get(testDirPath).getParent());
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	private static <T extends Type<T>> boolean equals(
			final Iterable<T> a,
			final Iterable<T> b) {
		final Iterator<T> itA = a.iterator();
		final Iterator<T> itB = b.iterator();
		while (itA.hasNext()) {
			if (!itA.next().valueEquals(itB.next()))
				return false;
		}
		return true;
	}

	@Test
	public final void test() throws IOException {

		final IHDF5Writer hdf5Writer = HDF5Factory.open(testDirPath);
		final N5HDF5Writer n5Writer = new N5HDF5Writer(hdf5Writer, blockSize);
		final ArrayImg<UnsignedByteType, ByteArray> img = ArrayImgs.unsignedBytes(bytes, dimensions);
		N5Utils.save(img, n5Writer, "/test", blockSize, new GzipCompression());
		hdf5Writer.close();

		final IHDF5Writer hdf5Reader = HDF5Factory.open(testDirPath);
		final N5HDF5Writer n5Reader = new N5HDF5Writer(hdf5Reader, blockSize);
		final RandomAccessibleInterval<UnsignedByteType> img2 = N5Utils.open(n5Reader, "/test");

		assertTrue(equals(img, Views.flatIterable(img2)));

		hdf5Reader.close();

		new N5HDF5Writer(testDirPath).remove();
	}
}
