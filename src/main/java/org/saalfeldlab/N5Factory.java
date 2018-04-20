package org.saalfeldlab;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;

import org.janelia.saalfeldlab.googlecloud.GoogleCloudClientSecretsCmdLinePrompt;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudClientSecretsPrompt;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudOAuth;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudResourceManagerClient;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageClient;
import org.janelia.saalfeldlab.googlecloud.GoogleCloudStorageURI;
import org.janelia.saalfeldlab.n5.Compression;
import org.janelia.saalfeldlab.n5.N5FSReader;
import org.janelia.saalfeldlab.n5.N5FSWriter;
import org.janelia.saalfeldlab.n5.N5Reader;
import org.janelia.saalfeldlab.n5.N5Writer;
import org.janelia.saalfeldlab.n5.googlecloud.N5GoogleCloudStorageReader;
import org.janelia.saalfeldlab.n5.googlecloud.N5GoogleCloudStorageWriter;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Reader;
import org.janelia.saalfeldlab.n5.hdf5.N5HDF5Writer;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Reader;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Writer;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.AmazonS3URI;
import com.google.auth.Credentials;
import com.google.cloud.resourcemanager.Project;
import com.google.cloud.resourcemanager.ResourceManager;
import com.google.cloud.storage.Storage;

import ch.systemsx.cisd.hdf5.HDF5Factory;

public class N5Factory {

	public static class N5Options {

		public final String containerPath;
		public final int[] blockSize;
		public final Compression compression;

		public N5Options(final String containerPath, final int[] blockSize, final Compression compression) {
			this.containerPath = containerPath;
			this.blockSize = blockSize;
			this.compression = compression;
		}
	}

	private static enum N5AccessType {

		Reader,
		Writer
	}

	public static N5Reader createN5Reader(final N5Options options) throws IOException {

		return createN5(options, N5AccessType.Reader);
	}

	public static N5Writer createN5Writer(final N5Options options) throws IOException {

		return createN5(options, N5AccessType.Writer);
	}

	@SuppressWarnings("unchecked")
	private static <N5 extends N5Reader> N5 createN5(final N5Options options, final N5AccessType accessType) throws IOException {

		final URI uri = URI.create(options.containerPath);
		if (uri.getScheme() == null) {
			if (isHDF5(options.containerPath, accessType))
				return (N5) createN5HDF5(options.containerPath, options.blockSize, accessType);
			else
				return (N5) createN5FS(options.containerPath, accessType);
		}

		if (uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https")) {
			// s3 uri parser is capable of parsing http links, try to parse it first as an s3 uri
			AmazonS3URI s3Uri;
			try {
				s3Uri = new AmazonS3URI(uri);
			} catch (final Exception e) {
				s3Uri = null;
			}

			if (s3Uri != null) {
				if (s3Uri.getBucket() == null || s3Uri.getBucket().isEmpty() || (s3Uri.getKey() != null && !s3Uri.getKey().isEmpty()))
					throw new IllegalArgumentException("N5 datasets on AWS S3 are stored in buckets. Please provide a link to a bucket.");
				return (N5) createN5S3(options.containerPath, accessType);
			} else {
				// might be a google cloud link
				final GoogleCloudStorageURI googleCloudUri;
				try {
					googleCloudUri = new GoogleCloudStorageURI(uri);
				} catch (final Exception e) {
					throw new IllegalArgumentException("Expected either a local path or a link to AWS S3 bucket / Google Cloud Storage bucket.");
				}

				if (googleCloudUri.getBucket() == null || googleCloudUri.getBucket().isEmpty() || (googleCloudUri.getKey() != null && !googleCloudUri.getKey().isEmpty()))
					throw new IllegalArgumentException("N5 datasets on Google Cloud are stored in buckets. Please provide a link to a bucket.");
				return (N5) createN5GoogleCloud(options.containerPath, accessType);
			}
		} else {
			switch (uri.getScheme().toLowerCase()) {
			case "file":
				final String parsedPath = Paths.get(uri).toString();
				if (isHDF5(parsedPath, accessType))
					return (N5) createN5HDF5(parsedPath, options.blockSize, accessType);
				else
					return (N5) createN5FS(parsedPath, accessType);
			case "s3":
				return (N5) createN5S3(options.containerPath, accessType);
			case "gs":
				return (N5) createN5GoogleCloud(options.containerPath, accessType);
			default:
				throw new IllegalArgumentException("Unsupported protocol: " + uri.getScheme());
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static <N5 extends N5FSReader> N5 createN5FS(final String containerPath, final N5AccessType accessType) throws IOException {

		switch (accessType) {
		case Reader:
			return (N5) new N5FSReader(containerPath);
		case Writer:
			return (N5) new N5FSWriter(containerPath);
		default:
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static <N5 extends N5HDF5Reader> N5 createN5HDF5(final String containerPath, final int[] blockSize, final N5AccessType accessType) throws IOException {

		switch (accessType) {
		case Reader:
			return (N5) new N5HDF5Reader(HDF5Factory.openForReading(containerPath), blockSize);
		case Writer:
			return (N5) new N5HDF5Writer(HDF5Factory.open(containerPath), blockSize);
		default:
			return null;
		}
	}

	private static boolean isHDF5(final String containerPath, final N5AccessType accessType) {

		switch (accessType) {
		case Reader:
			return Files.isRegularFile(Paths.get(containerPath));
		case Writer:
			return containerPath.toLowerCase().endsWith(".h5") || containerPath.toLowerCase().endsWith(".hdf5") || containerPath.toLowerCase().endsWith(".hdf");
		default:
			throw new RuntimeException();
		}
	}

	@SuppressWarnings("unchecked")
	private static <N5 extends N5AmazonS3Reader> N5 createN5S3(final String containerPath, final N5AccessType accessType) throws IOException {

		final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withCredentials(new ProfileCredentialsProvider()).build();

		final AmazonS3URI s3Uri = new AmazonS3URI(containerPath);
		final String bucketName = s3Uri.getBucket();

		switch (accessType) {
		case Reader:
			return (N5) new N5AmazonS3Reader(s3, bucketName);
		case Writer:
			return (N5) new N5AmazonS3Writer(s3, bucketName);
		default:
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static <N5 extends N5GoogleCloudStorageReader> N5 createN5GoogleCloud(final String containerPath, final N5AccessType accessType) throws IOException {

		final GoogleCloudClientSecretsPrompt clientSecretsPrompt = new GoogleCloudClientSecretsCmdLinePrompt();
		final GoogleCloudOAuth oauth = new GoogleCloudOAuth(clientSecretsPrompt);
		final Credentials credentials = oauth.getCredentials();

		final GoogleCloudStorageClient storageClient;
		switch (accessType) {
		case Reader:
			storageClient = new GoogleCloudStorageClient(credentials);
			break;
		case Writer:
			storageClient = new GoogleCloudStorageClient(credentials, getGoogleCloudProjectId(credentials));
			break;
		default:
			storageClient = null;
		}
		final Storage storage = storageClient.create();

		final GoogleCloudStorageURI googleCloudUri = new GoogleCloudStorageURI(containerPath);
		final String bucketName = googleCloudUri.getBucket();

		switch (accessType) {
		case Reader:
			return (N5) new N5GoogleCloudStorageReader(storage, bucketName);
		case Writer:
			return (N5) new N5GoogleCloudStorageWriter(storage, bucketName);
		default:
			return null;
		}
	}

	private static String getGoogleCloudProjectId(final Credentials credentials) {

		// FIXME: get first project id for now
		// TODO: prompt user for project id
		final ResourceManager resourceManager = new GoogleCloudResourceManagerClient(credentials).create();
		final Iterator<Project> projectsIterator = resourceManager.list().iterateAll().iterator();
		if (!projectsIterator.hasNext())
			throw new RuntimeException("No projects were found. Create a google cloud project first");
		final String projectId = projectsIterator.next().getProjectId();

		return projectId;
	}
}
