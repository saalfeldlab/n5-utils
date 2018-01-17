# n5-utils
simple standalone BigDataViewer for multiple N5 (or HDF5) datasets
and also a copy tool

Build a fat jar with
```bash
mvn clean package
```
If this fails, check out the missing SNAPSHOT dependency projects (bigdataviewer-core, bigdataviewer-vistools at branch volatiletypes) from GitHub and
```bash
mvn clean install
```
them.  Sorry for that, but we need these changes and there are no releases yet.

Run the viewer
```bash
java -jar target/simple-viewer-0.0.1-SNAPSHOT.jar \
  -i '/path/file.h5' \
  -i '/path/file.hdf5' \
  -d /volumes/raw \
  -d /prediction \
  -c 0,255 \
  -c -1,1 \
  -r 1,1,10
```
to look at two datasets from an HDF5 and an N5 container at 1x1x10 resolution and contrast ranges [0,255] and [-1,1].  The viewer maps both contrast ranges into [0,1000] because BDV cannot yet deal with negative intensities and the sliders only show integers.  Sorry for the hack.

Copy from N5/HDF5 to N5/HDF5
```bash
java -cp target/simple-viewer-0.0.1-SNAPSHOT.jar org.saalfeldlab.Copy \
  -i '/path/file.hdf5' \
  -o '/path/file.n5' \
  -b 256,256,26
  -c gzip
```
or
```bash
java -cp target/simple-viewer-0.0.1-SNAPSHOT.jar org.saalfeldlab.Copy \
  -i '/path/file.hdf5' \
  -o '/path/file.n5' \
  -b 256,256,26
  -c gzip
  -d /volumes
```
for one or more groups/ datasets.
