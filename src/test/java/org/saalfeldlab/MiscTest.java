package org.saalfeldlab;

import static org.junit.Assert.assertArrayEquals;

import java.util.Arrays;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class MiscTest {

	static final int[][] axess = new int[][]{
			{0, 1},
			{1, 2, 0},
			{4, 6, 5},
			{2, 4, 0}
	};

	static final int[] ns = new int[]{2, 4, 10, 7};

	static final int[][] allAxess = new int[][]{
			{0, 1},
			{1, 2, 0, 3},
			{4, 6, 5, 0, 1, 2, 3, 7, 8, 9},
			{2, 4, 0, 1, 3, 5, 6}
	};

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public final void testAllAxes() {

		for (int i = 0; i < ns.length; ++i)
			assertArrayEquals(allAxess[i], allAxes(axess[i], ns[i]));
	}

	private static final int[] allAxes(final int[] axes, final int n) {

		final int[] sortedAxes = axes.clone();
		Arrays.sort(sortedAxes);
		final int[] allAxes = Arrays.copyOf(axes, n);
		int b = sortedAxes.length;
		int c = 0;
		for (int a = 0; a < axes.length; ++c) {
			if (sortedAxes[a] > c)
				allAxes[b++] = c;
			else
				++a;
		}
		for (; c < allAxes.length; ++c, ++b)
			allAxes[b] = c;

		return allAxes;
	}

}
