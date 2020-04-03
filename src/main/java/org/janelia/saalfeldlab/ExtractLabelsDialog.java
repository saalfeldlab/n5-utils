package org.janelia.saalfeldlab;

import bdv.viewer.Source;
import bdv.viewer.ViewerPanel;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.util.Pair;
import org.scijava.ui.behaviour.*;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.InputActionBindings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.*;

public class ExtractLabelsDialog< T extends NumericType< T > & NativeType< T > >
{
    final protected ViewerPanel viewer;

    private final RealPoint lastClick = new RealPoint( 3 );
    private final List<Pair<String, Source<T>>> datasetsAndSources;
    private final String inputContainer;
    private final String outputPath;

    static private int width = 1024;
    static private int height = 1024;
    static private int depth = 512;
    static private int scaling = 2;
    static private int threshold = 128;
    static private int blockSize = 128;

    // for behavioUrs
    private final BehaviourMap behaviourMap = new BehaviourMap();
    private final InputTriggerMap inputTriggerMap = new InputTriggerMap();
    private final InputTriggerAdder inputAdder;

    // for keystroke actions
    private final ActionMap ksActionMap = new ActionMap();
    private final InputMap ksInputMap = new InputMap();

    public ExtractLabelsDialog(
            final ViewerPanel viewer,
            final List<Pair<String, Source<T>>> datasetsAndSources,
            final String inputContainer,
            final String outputPath,
            final InputTriggerConfig config,
            final InputActionBindings inputActionBindings,
            final KeyStrokeAdder.Factory keyProperties )
    {
        this.viewer = viewer;
        this.datasetsAndSources = datasetsAndSources;
        this.inputContainer = inputContainer;
        this.outputPath = outputPath;

        inputAdder = config.inputTriggerAdder( inputTriggerMap, "crop" );

        new Crop( "crop", "SPACE" ).register();

        inputActionBindings.addActionMap( "select", ksActionMap );
        inputActionBindings.addInputMap( "select", ksInputMap );
    }

    ////////////////
    // behavioUrs //
    ////////////////

    public BehaviourMap getBehaviourMap()
    {
        return behaviourMap;
    }

    public InputTriggerMap getInputTriggerMap()
    {
        return inputTriggerMap;
    }

    private abstract class SelfRegisteringBehaviour implements Behaviour
    {
        private final String name;
        private final String[] defaultTriggers;

        public SelfRegisteringBehaviour( final String name, final String... defaultTriggers )
        {
            this.name = name;
            this.defaultTriggers = defaultTriggers;
        }

        public void register()
        {
            behaviourMap.put( name, this );
            inputAdder.put( name, defaultTriggers );
        }
    }

    private class Crop extends SelfRegisteringBehaviour implements ClickBehaviour
    {
        private	List< TextField > centerPointTextFields;
        private long[] centerPoint;

        public Crop( final String name, final String ... defaultTriggers )
        {
            super( name, defaultTriggers );
        }

        @Override
        public void click( final int x, final int y )
        {
            viewer.displayToGlobalCoordinates(x, y, lastClick);

            centerPoint = new long[ lastClick.numDimensions() ];
            for ( int i = 0; i < centerPoint.length; ++i )
                centerPoint[ i ] = Math.round( lastClick.getDoublePosition( i ) );

            final GenericDialog gd = new GenericDialog( "Extract labels" );

            gd.addCheckbox( "Custom_center_point", false );
            gd.addNumericField( "X : ", centerPoint[ 0 ], 0 );
            gd.addNumericField( "Y : ", centerPoint[ 1 ], 0 );
            gd.addNumericField( "Z : ", centerPoint[ 2 ], 0 );
            gd.addPanel( new Panel() );
            gd.addNumericField( "width : ", width, 0, 5, "px" );
            gd.addNumericField( "height : ", height, 0, 5, "px" );
            gd.addNumericField( "depth : ", depth, 0, 5, "px" );
            gd.addNumericField( "scaling : ", scaling, 0, 5, "" );
            gd.addNumericField( "threshold : ", threshold, 0, 5, "" );
            gd.addNumericField( "block size: ", blockSize, 0, 5, "" );

            gd.addPanel( new Panel() );
            final String[] datasetLabels = new String[datasetsAndSources.size()];
            Arrays.setAll(datasetLabels, i -> datasetsAndSources.get(i).getA());
            int numColumns = 3;
            int numRows = (datasetLabels.length / numColumns) + (datasetLabels.length % numColumns == 0 ? 0 : 1);
            gd.addCheckboxGroup(numRows, numColumns, datasetLabels, new boolean[datasetLabels.length]);


            final Button selectAllBtn = new Button("Select All"), selectNoneBtn = new Button("Select none");
            final Panel selectBtnPanel = new Panel();
            selectBtnPanel.add(selectAllBtn);
            selectBtnPanel.add(selectNoneBtn);
            gd.add(selectBtnPanel);

            gd.addMessage("Output path: " + outputPath);
//            gd.addNumericField( "scale_level : ", scaleLevel, 0 );
//            gd.addCheckbox( "Single_4D_stack", single4DStack );

            centerPointTextFields = new ArrayList<>();
            for ( int i = 0; i < 3; ++i )
                centerPointTextFields.add( ( TextField ) gd.getNumericFields().get( i ) );

            final Checkbox centerPointCheckbox = ( Checkbox ) gd.getCheckboxes().get( 0 );
            centerPointCheckbox.addItemListener(
                    new ItemListener()
                    {
                        @Override
                        public void itemStateChanged( final ItemEvent e )
                        {
                            setCenterPointTextFieldsEnabled( e.getStateChange() == ItemEvent.SELECTED );
                        }
                    }
            );

            gd.addComponentListener( new ComponentAdapter()
                                     {
                                         @Override
                                         public void componentShown( final ComponentEvent e )
                                         {
                                             setCenterPointTextFieldsEnabled( false );
                                         }
                                     }
            );



            // add 'select all' and 'select none' listeners
            final List<Checkbox> datasetCheckboxes = new ArrayList<>();
            for (int i = 0; i < datasetLabels.length; ++i)
                datasetCheckboxes.add( (Checkbox) gd.getCheckboxes().get( i + 1 ) );
            selectAllBtn.addActionListener(
                    new ActionListener()
                    {
                        @Override
                        public void actionPerformed( final ActionEvent e )
                        {
                            for ( final Checkbox c : datasetCheckboxes)
                                c.setState(true);
                        }
                    }
            );

            selectNoneBtn.addActionListener(
                    new ActionListener()
                    {
                        @Override
                        public void actionPerformed( final ActionEvent e )
                        {
                            for ( final Checkbox c : datasetCheckboxes)
                                c.setState(false);
                        }
                    }
            );


            gd.showDialog();

            if ( gd.wasCanceled() )
                return;

            final boolean customCenterPoint = gd.getNextBoolean();
            for ( int i = 0; i < 3; ++i )
                centerPoint[ i ] = ( long ) gd.getNextNumber();

            width = ( int )gd.getNextNumber();
            height = ( int )gd.getNextNumber();
            depth = ( int )gd.getNextNumber();
            scaling = (int)gd.getNextNumber();
            threshold = (int)gd.getNextNumber();
            blockSize = (int)gd.getNextNumber();
//            scaleLevel = ( int )gd.getNextNumber();
//            single4DStack = gd.getNextBoolean();

//            final int s = scaleLevel;
            final int scaleLevel = 0;
            final int timepoint = 1;

            final String centerPosStr = Arrays.toString( centerPoint );
            if ( customCenterPoint )
                lastClick.setPosition( centerPoint );

//                if ( s < 0 || s >= source.getNumMipmapLevels() )
//                {
//                    IJ.log( String.format( "Specified incorrect scale level %d. Valid range is [%d, %d]", s, 0, source.getNumMipmapLevels() - 1 ) );
//                    scaleLevel = source.getNumMipmapLevels() - 1;
//                    return;
//                }

            final AffineTransform3D transform = new AffineTransform3D();
            datasetsAndSources.get( 0 ).getB().getSourceTransform( timepoint, scaleLevel, transform );

            final RealPoint center = new RealPoint( 3 );
            transform.applyInverse( center, lastClick );

            final long[] size = new long[] { width, height, depth };
            final long[] min = new long[3], max = new long[3];
            Arrays.setAll(min, d -> Math.round( center.getDoublePosition(d) - 0.5 * size[d] ) );
            Arrays.setAll(max, d -> min[d] + size[d] - 1);
            final Interval cropInterval = new FinalInterval(min, max);

            final int[] blockSizeArr = new int[3];
            Arrays.fill(blockSizeArr, blockSize);

            final List<String> datasetsToExtract = new ArrayList<>();
            for (int i = 0; i < datasetLabels.length; ++i)
                if ( datasetCheckboxes.get(i).getState())
                    datasetsToExtract.add(datasetLabels[i]);

            try {
                System.out.println("Extracting the following classes in interval min=" + Arrays.toString(min) + ", max=" + Arrays.toString(max) + " to " + outputPath + ":");
                for (final String d : datasetsToExtract)
                    System.out.println("  " + d);

                ExtractLabels.extractLabels(
                        inputContainer,
                        cropInterval,
                        outputPath,
                        OptionalDouble.of(scaling),
                        OptionalDouble.of(threshold),
                        Optional.of(datasetsToExtract),
                        Optional.of(blockSizeArr));

                System.out.println("Done!");
            } catch (final Exception e) {
                e.printStackTrace();
            }

//                final RandomAccessibleInterval< T > img = source.getSource( 0, s );
//                final RandomAccessible< T > imgExtended = Views.extendZero( img );
//                final IntervalView< T > crop = Views.offsetInterval( imgExtended, min, size );
//
//                channelsImages.add( crop );

//                if ( !single4DStack )
//                    show( crop, "channel " + channel + " " + centerPosStr );

//            if ( single4DStack )
//            {
//                // FIXME: need to permute slices/channels. Swapping them in the resulting ImagePlus produces wrong output
//                ImageJFunctions.show( Views.permute( Views.stack( channelsImages ), 2, 3 ), centerPosStr );
//            }

            viewer.requestRepaint();
        }

        // Taken from ImageJFunctions. Modified to swap slices/channels for 3D image (by default they mistakenly are nSlices=1 and nChannels=depth)
        // TODO: pull request with this fix if appropriate in general case?
        private ImagePlus show( final RandomAccessibleInterval< T > img, final String title )
        {
//            final ImagePlus imp = ImageJFunctions.wrap( img, title );
//            if ( null == imp ) { return null; }
//
//            // Make sure that nSlices>1 and nChannels=nFrames=1 for 3D image
//            final int[] possible3rdDim = new int[] { imp.getNChannels(), imp.getNSlices(), imp.getNFrames() };
//            Arrays.sort( possible3rdDim );
//            if ( possible3rdDim[ 0 ] * possible3rdDim[ 1 ] == 1 )
//                imp.setDimensions( 1, possible3rdDim[ 2 ], 1 );
//
//            imp.show();
//            imp.getProcessor().resetMinAndMax();
//            imp.updateAndRepaintWindow();
//
//            return imp;
            return null;
        }

        private void setCenterPointTextFieldsEnabled( final boolean enabled )
        {
            for ( int i = 0; i < centerPointTextFields.size(); ++i )
            {
                final TextField tf = centerPointTextFields.get( i );
                tf.setEnabled( enabled );

                if ( !enabled )
                    tf.setText( Long.toString( centerPoint[ i ] ) );
            }
        }
    }
}