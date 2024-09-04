[![Build Status](https://github.com/saalfeldlab/n5-utils/actions/workflows/build-main.yml/badge.svg)](https://github.com/saalfeldlab/n5-utils/actions/workflows/build-main.yml)

# n5-utils

https://github.com/user-attachments/assets/62d3415e-7f7c-4b43-886f-5e6de8a5b8a1

Collection of standalone command line tools to work with HDF5/ Zarr/ N5 datasets.  Including:

- A simple standalone BigDataViewer for multiple datasets or mipmap pyramids.
- A copy, re-chunking, and re-compressing tool for individual datasets or groups.
- A `unique` tool to get the set of unique numbers in a dataset.
- An `equals` tool to compare two datasets.

Installation requires maven, OpenJDK, lib-hdf5, [and libblosc] on Ubuntu:
```bash
sudo apt-get install openjdk-21-jdk maven hdf5-tools libblosc1
```
On other platforms, please find your way and report back if interested.

Install into your favorite local binary `$PATH`:
```bash
./install $HOME/bin
```
All dependencies will be downloaded and managed by maven automatically.

This installs the tools, `n5-view`, `n5-copy`, `n5-equals`, and `n5-unique`

Run the viewer
```bash
n5-view \
  -i '/path/file.h5' \
  -i '/path/file.hdf5' \
  -d /volumes/raw \
  -d /prediction \
  -c 0,255 \
  -c -1,1 \
  -r 1,1,10
```
to look at two datasets or mipmap pyramids from an HDF5 and N5 container at 1x1x10 resolution and contrast ranges [0,255] and [-1,1].  The viewer maps both contrast ranges into [0,1000] because BDV cannot yet deal with negative intensities and the sliders only show integers.  Sorry for the hack.

Copy from N5/HDF5 to N5/HDF5:
```bash
n5-copy \
  -i '/path/file.hdf5' \
  -o '/path/file.n5' \
  -b 256,256,26
  -c gzip
```
or for one or more groups/ datasets:
```bash
n5-copy \
  -i '/path/file.hdf5' \
  -o '/path/file.n5' \
  -b 256,256,26
  -c gzip
  -d /volumes
```

# Use as a library in Fiji

This project can be used to deploy the most useful n5 libraries into an existing Fiji installation where they can be used for scripting.  Thanks to the [scijava-maven-plugin](https://github.com/scijava/scijava-maven-plugin), you can do this by simply passing your Fiji installation path to maven
```
mvn -Dimagej.app.directory=$HOME/packages/Fiji.app
```
Then try the experiments in this [script](https://github.com/saalfeldlab/n5-utils/blob/master/scripts/n5-examples.bsh) with your own data.
