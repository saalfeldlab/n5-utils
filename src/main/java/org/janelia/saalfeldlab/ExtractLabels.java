package org.janelia.saalfeldlab;

import net.imglib2.*;
import net.imglib2.converter.Converters;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Scale3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.*;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Igor Pisarev
 */
public class ExtractLabels<T extends NativeType<T> & RealType<T>> implements Callable<Void> {

    @CommandLine.Option(names = {"-i", "--container"}, required = true, description = "container path, e.g. -i $HOME/fib19.n5")
    private String containerPath = null;

    @CommandLine.Option(names = {"-d", "--datasets"}, required = false, description = "datasets (optional, by default all will be included)")
    private List<String> datasets = null;

    @CommandLine.Option(names = {"-o", "--output"}, required = true, description = "output container")
    private String outputPath = null;

    @CommandLine.Option(names = {"-min", "--min"}, required = true, description = "crop min")
    private String cropMinStr = null;

    @CommandLine.Option(names = {"-max", "--max"}, required = true, description = "crop max")
    private String cropMaxStr = null;

    @CommandLine.Option(names = {"-b", "--blockSize"}, required = false, description = "block size")
    private String blockSizeStr = null;

    @CommandLine.Option(names = {"-t", "--threshold"}, required = false, description = "threshold")
    private double threshold = 128;

    @CommandLine.Option(names = {"-s", "--scale"}, required = false, description = "scaling")
    private double scaling = 2;

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

    protected static final int[] parseCSIntArray(final String csv) {

        final String[] stringValues = csv.split(",\\s*");
        final int[] array = new int[stringValues.length];
        try {
            for (int i = 0; i < array.length; ++i)
                array[i] = Integer.parseInt(stringValues[i]);
        } catch (final NumberFormatException e) {
            e.printStackTrace(System.err);
            return null;
        }
        return array;
    }

    @Override
    public Void call() throws IOException, ExecutionException, InterruptedException {

        final N5Reader n5Reader = new N5FSReader(containerPath);
        if (datasets == null)
            datasets = Arrays.asList(n5Reader.list("/"));

        final Interval cropInterval = new FinalInterval(
                parseCSLongArray(cropMinStr),
                parseCSLongArray(cropMaxStr));

        final AffineTransform3D upscaleTransform = new AffineTransform3D();
        upscaleTransform
                .preConcatenate(new Scale3D(scaling, scaling, scaling));
//                .preConcatenate(new Translation3D(-0.5, -0.5, -0.5));

        final ExecutorService threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        final int[] blockSize = blockSizeStr != null ? parseCSIntArray(blockSizeStr) : null;

        for (final String dataset : datasets) {
            final RandomAccessibleInterval<T> img = N5Utils.open(n5Reader, dataset);
            final RealRandomAccessible<T> interpolatedImg = Views.interpolate(Views.extendBorder(img), new NLinearInterpolatorFactory<>());
            final RandomAccessible<T> upscaledImg = RealViews.affine(interpolatedImg, upscaleTransform);

            final double[] upscaledCropMin = new double[3], upscaledCropMax = new double[3];
            Arrays.setAll(upscaledCropMin, d -> cropInterval.realMin(d));
            Arrays.setAll(upscaledCropMax, d -> cropInterval.realMax(d) + 1);
            upscaleTransform.apply(upscaledCropMin, upscaledCropMin);
            upscaleTransform.apply(upscaledCropMax, upscaledCropMax);
            Arrays.setAll(upscaledCropMax, d -> upscaledCropMax[d] - 1);
            final Interval upscaledCropInterval = Intervals.smallestContainingInterval(new FinalRealInterval(upscaledCropMin, upscaledCropMax));
            final RandomAccessibleInterval<T> upscaledCrop = Views.interval(upscaledImg, upscaledCropInterval);
            final RandomAccessibleInterval<ByteType> upscaledCropMask = Converters.convert(upscaledCrop, (in, out) -> out.set(in.getRealDouble() >= threshold ? (byte)1 : 0), new ByteType());

            final int[] outBlockSize = blockSize != null ? blockSize : n5Reader.getDatasetAttributes(dataset).getBlockSize();
            final String outputDatasetPath = Paths.get("volumes/labels", dataset).toString();
            final N5Writer n5Writer = new N5FSWriter(outputPath);

            N5Utils.save(
                    upscaledCropMask,
                    n5Writer,
                    outputDatasetPath,
                    outBlockSize,
                    new GzipCompression(),
                    threadPool);

            final double[] inputResolution = n5Reader.getAttribute(dataset, "resolution", double[].class);
            final double[] outputResolution = new double[3];
            Arrays.setAll(outputResolution, d -> (inputResolution != null ? inputResolution[d] : 1) * scaling);
            n5Writer.setAttribute(outputDatasetPath, "resolution", outputResolution);

            final double[] inputOffset = n5Reader.getAttribute(dataset, "offset", double[].class);
            final double[] outputOffset = new double[3];
            Arrays.setAll(outputOffset, d -> (inputOffset != null ? inputOffset[d] : 0) + cropInterval.realMin(d));
            n5Writer.setAttribute(outputDatasetPath, "offset", outputOffset);
        }

        threadPool.shutdown();

        return null;
    }

    @SuppressWarnings( "unchecked" )
    public static final void main(final String... args) {

        CommandLine.call(new ExtractLabels(), args);
    }
}
