package org.janelia.saalfeldlab;

import net.imglib2.*;
import net.imglib2.interpolation.randomaccess.NearestNeighborInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.GzipCompression;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import picocli.CommandLine;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * @author Igor Pisarev
 */
public class ExtractLabels<T extends NativeType<T> & RealType<T>> implements Callable<Void> {

    @CommandLine.Option(names = {"-i", "--container"}, required = true, description = "container path, e.g. -i $HOME/fib19.n5")
    private String containerPath = null;

    @CommandLine.Option(names = {"-d", "--datasets"}, required = false, description = "datasets (optional, by default all will be included)")
    private List<String> datasets = null;

    @CommandLine.Option(names = {"-o", "--output"}, required = true, description = "output path")
    private String outputPath = null;

    @CommandLine.Option(names = {"-min", "--min"}, required = true, description = "crop min")
    private String cropMinStr = null;

    @CommandLine.Option(names = {"-max", "--max"}, required = true, description = "crop max")
    private String cropMaxStr = null;

    protected static final long[] parseCSLongArray(final String csv) {

        final String[] stringValues = csv.split(",\\s*");
        final long[] array = new long[stringValues.length];
        try {
            for (int i = 0; i < array.length; ++i)
                array[i] = Long.parseLong(stringValues[i]);
        } catch (final NumberFormatException e) {
            e.printStackTrace(System.err);
            return null;
        }
        return array;
    }

    @Override
    public Void call() throws IOException {

        final N5Reader n5 = N5Factory.createN5Reader(new N5Factory.N5Options(containerPath, new int[] {64, 64, 64}, new GzipCompression()));
        if (datasets == null)
            datasets = Arrays.asList(n5.list("/"));

        final Interval cropInterval = new FinalInterval(
                parseCSLongArray(cropMinStr),
                parseCSLongArray(cropMaxStr));

        final AffineTransform3D upscaleTransform = new AffineTransform3D();
        upscaleTransform
                .preConcatenate(new Scale3D(2, 2, 2));
//                .preConcatenate(new Translation3D(-0.5, -0.5, -0.5));

        for (final String dataset : datasets) {
            final RandomAccessibleInterval<T> img = N5Utils.open(n5, dataset);
            final RealRandomAccessible<T> interpolatedImg = Views.interpolate(Views.extendBorder(img), new NearestNeighborInterpolatorFactory<>());
            final RandomAccessible<T> upscaledImg = RealViews.affine(interpolatedImg, upscaleTransform);

            final double[] upscaledCropMin = new double[3], upscaledCropMax = new double[3];
            Arrays.setAll(upscaledCropMin, d -> cropInterval.realMin(d));
            Arrays.setAll(upscaledCropMax, d -> cropInterval.realMax(d) + 1);
            upscaleTransform.apply(upscaledCropMin, upscaledCropMin);
            upscaleTransform.apply(upscaledCropMax, upscaledCropMax);
            Arrays.setAll(upscaledCropMax, d -> upscaledCropMax[d] - 1);
            final Interval upscaledCropInterval = Intervals.smallestContainingInterval(new FinalRealInterval(upscaledCropMin, upscaledCropMax));

            final RandomAccessibleInterval<T> upscaledCrop = Views.interval(upscaledImg, upscaledCropInterval);

//            threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
//            N5Utils.save(upscaledImg, new N5FSWriter(container), outputDataset, blockSize, new GzipCompression(), threadPool);
//            threadPool.shutdown();
        }

        return null;
    }

    @SuppressWarnings( "unchecked" )
    public static final void main(final String... args) {

        CommandLine.call(new ExtractLabels(), args);
    }
}
