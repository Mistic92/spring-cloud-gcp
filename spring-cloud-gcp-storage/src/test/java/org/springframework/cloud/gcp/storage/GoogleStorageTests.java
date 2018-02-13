/*
 *  Copyright 2017 original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.gcp.storage;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.util.concurrent.TimeUnit;

import com.google.cloud.WriteChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.WritableResource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Vinicius Carvalho
 * @author Artem Bilan
 * @author Mike Eltsufin
 */
@SpringBootTest
@RunWith(SpringRunner.class)
public class GoogleStorageTests {

	@Value("gs://test-spring/images/spring.png")
	private Resource remoteResource;

	@Value("gs://test_spring/images/spring.png")
	private Resource remoteResourceWithUnderscore;

	@Value("gs://test-spring/")
	private Resource bucketResource;

	@Autowired
	private Storage storage;

	@Test(expected = IllegalArgumentException.class)
	public void testEmptyPath() {
		new GoogleStorageResource(this.storage, "gs://", false);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSlashPath() {
		new GoogleStorageResource(this.storage, "gs:///", false);
	}


	@Test
	public void testValidObject() throws Exception {
		Assert.assertTrue(this.remoteResource.exists());
		Assert.assertEquals(4096L, this.remoteResource.contentLength());
	}

	@Test
	public void testValidObjectWithUnderscore() throws Exception {
		Assert.assertTrue(this.remoteResourceWithUnderscore.exists());
	}

	@Test
	public void testValidBucket() throws IOException {
		Assert.assertEquals("gs://test-spring/", this.bucketResource.getDescription());
		Assert.assertEquals("test-spring", this.bucketResource.getFilename());
		Assert.assertEquals("gs://test-spring/", this.bucketResource.getURI().toString());

		String relative = this.bucketResource.createRelative("aaa/bbb").getURI().toString();
		Assert.assertEquals("gs://test-spring/aaa/bbb", relative);
		Assert.assertEquals(relative, this.bucketResource.createRelative("/aaa/bbb").getURI().toString());

		Assert.assertNull(((GoogleStorageResource) this.bucketResource).getBlobName());
		Assert.assertTrue(((GoogleStorageResource) this.bucketResource).isBucket());

		Assert.assertTrue(this.bucketResource.exists());
		Assert.assertTrue(((GoogleStorageResource) this.bucketResource).bucketExists());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testBucketNotEndingInSlash() {
		new GoogleStorageResource(this.storage, "gs://test-spring");
	}

	@Test(expected = IllegalStateException.class)
	public void testBucketOutputStream() throws IOException {
		((WritableResource) this.bucketResource).getOutputStream();
	}

	@Test(expected = IllegalStateException.class)
	public void testBucketNoBlobInputStream() throws IOException {
		this.bucketResource.getInputStream();
	}

	@Test(expected = IllegalStateException.class)
	public void testBucketNoBlobContentLength() throws IOException {
		this.bucketResource.contentLength();
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testBucketNoBlobFile() throws IOException {
		this.bucketResource.getFile();
	}

	@Test(expected = IllegalStateException.class)
	public void testBucketNoBlobLastModified() throws IOException {
		this.bucketResource.lastModified();
	}

	@Test
	public void testBucketNoBlobResourceStatuses() throws IOException {
		Assert.assertFalse(this.bucketResource.isOpen());
		Assert.assertFalse(this.bucketResource.isReadable());
		Assert.assertFalse(((WritableResource) this.bucketResource).isWritable());
		Assert.assertTrue(this.bucketResource.exists());
	}

	@Test
	public void testWritable() throws Exception {
		Assert.assertTrue(this.remoteResource instanceof WritableResource);
		WritableResource writableResource = (WritableResource) this.remoteResource;
		Assert.assertTrue(writableResource.isWritable());
		writableResource.getOutputStream();
	}

	@Test
	public void testWritableOutputStream() throws Exception {
		String location = "gs://test-spring/test";
		Storage storage = mock(Storage.class);
		Blob blob = mock(Blob.class);
		WriteChannel writeChannel = mock(WriteChannel.class);
		when(blob.writer()).thenReturn(writeChannel);
		when(blob.exists()).thenReturn(true);
		when(storage.get(BlobId.of("test-spring", "test"))).thenReturn(blob);

		GoogleStorageResource resource = new GoogleStorageResource(storage, location);
		OutputStream os = resource.getOutputStream();
		Assert.assertNotNull(os);
	}

	@Test(expected = FileNotFoundException.class)
	public void testWritableOutputStreamNoAutoCreateOnNullBlob() throws Exception {
		String location = "gs://test-spring/test";
		Storage storage = mock(Storage.class);
		when(storage.get(BlobId.of("test-spring", "test"))).thenReturn(null);

		GoogleStorageResource resource = new GoogleStorageResource(storage, location,
				false);
		resource.getOutputStream();
	}

	@Test
	public void testWritableOutputStreamWithAutoCreateOnNullBlob() throws Exception {
		String location = "gs://test-spring/test";
		Storage storage = mock(Storage.class);
		BlobId blobId = BlobId.of("test-spring", "test");
		when(storage.get(blobId)).thenReturn(null);
		BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
		WriteChannel writeChannel = mock(WriteChannel.class);
		Blob blob = mock(Blob.class);
		when(blob.writer()).thenReturn(writeChannel);
		when(storage.create(blobInfo)).thenReturn(blob);

		GoogleStorageResource resource = new GoogleStorageResource(storage, location);
		GoogleStorageResource spyResource = spy(resource);
		OutputStream os = spyResource.getOutputStream();
		Assert.assertNotNull(os);
	}

	@Test
	public void testWritableOutputStreamWithAutoCreateOnNonExistantBlob()
			throws Exception {
		String location = "gs://test-spring/test";
		Storage storage = mock(Storage.class);
		BlobId blobId = BlobId.of("test-spring", "test");
		Blob nonExistantBlob = mock(Blob.class);
		when(nonExistantBlob.exists()).thenReturn(false);
		when(storage.get(blobId)).thenReturn(nonExistantBlob);
		BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
		WriteChannel writeChannel = mock(WriteChannel.class);
		Blob blob = mock(Blob.class);
		when(blob.writer()).thenReturn(writeChannel);
		when(storage.create(blobInfo)).thenReturn(blob);


		GoogleStorageResource resource = new GoogleStorageResource(storage, location);
		GoogleStorageResource spyResource = spy(resource);
		OutputStream os = spyResource.getOutputStream();
		Assert.assertNotNull(os);
	}

	@Test(expected = FileNotFoundException.class)
	public void testGetInputStreamOnNullBlob() throws Exception {
		String location = "gs://test-spring/test";
		Storage storage = mock(Storage.class);
		when(storage.get(BlobId.of("test-spring", "test"))).thenReturn(null);

		GoogleStorageResource resource = new GoogleStorageResource(storage, location,
				false);
		resource.getInputStream();
	}

	@Test
	public void testGetFilenameOnNonExistingBlob() throws Exception {
		String location = "gs://test-spring/test";
		Storage storage = mock(Storage.class);
		when(storage.get(BlobId.of("test-spring", "test"))).thenReturn(null);

		GoogleStorageResource resource = new GoogleStorageResource(storage, location,
				false);
		Assert.assertEquals("test", resource.getFilename());
	}

	@Test
	public void testisAutoCreateFilesGetterSetter() {
		String location = "gs://test-spring/test";
		Storage storage = mock(Storage.class);
		GoogleStorageResource resource = new GoogleStorageResource(storage, location);
		Assert.assertTrue(resource.isAutoCreateFiles());
	}

	@Test
	public void testCreateRelative() throws IOException {
		String location = "gs://test-spring/test.png";
		Storage storage = mock(Storage.class);
		GoogleStorageResource resource = new GoogleStorageResource(storage, location);
		GoogleStorageResource relative = (GoogleStorageResource) resource.createRelative("relative.png");
		Assert.assertEquals("gs://test-spring/relative.png", relative.getURI().toString());
	}

	@Test
	public void testCreateRelativeSubdirs() throws IOException {
		String location = "gs://test-spring/t1/test.png";
		Storage storage = mock(Storage.class);
		GoogleStorageResource resource = new GoogleStorageResource(storage, location);
		GoogleStorageResource relative = (GoogleStorageResource) resource.createRelative("r1/relative.png");
		Assert.assertEquals("gs://test-spring/t1/r1/relative.png", relative.getURI().toString());
	}

	@Test
	public void nullSignedUrlForNullBlob() throws IOException {
		String location = "gs://test-spring/t1/test.png";
		Storage storage = mock(Storage.class);
		GoogleStorageResource resource =
				new GoogleStorageResource(storage, location, false);
		when(storage.get(any(BlobId.class))).thenReturn(null);
		Assert.assertNull(resource.createSignedUrl(TimeUnit.DAYS, 1));
	}

	@Test
	public void signedUrlFunctionCalled() throws IOException {
		String location = "gs://test-spring/t1/test.png";
		Storage storage = mock(Storage.class);
		Blob blob = mock(Blob.class);
		when(blob.getBucket()).thenReturn("fakeBucket");
		when(blob.getName()).thenReturn("fakeObject");
		GoogleStorageResource resource = new GoogleStorageResource(storage, location);
		when(storage.get(any(BlobId.class))).thenReturn(blob);
		resource.createSignedUrl(TimeUnit.DAYS, 1L);
		verify(storage, times(1)).signUrl(any(), eq(1L), eq(TimeUnit.DAYS));
	}

	@Test(expected = MalformedURLException.class)
	public void getUrlFails() throws IOException {
		this.remoteResource.getURL();
	}

	@Test
	public void testBucketDoesNotExist() {
		GoogleStorageResource bucket = new GoogleStorageResource(this.storage, "gs://non-existing/");
		Assert.assertFalse(bucket.bucketExists());
		Assert.assertFalse(bucket.exists());

		Assert.assertNotNull(bucket.createBucket());
	}

	@Test
	public void testBucketExistsButResourceDoesNot() {
		GoogleStorageResource resource = new GoogleStorageResource(this.storage, "gs://test-spring/file1");
		Assert.assertTrue(resource.bucketExists());
		Assert.assertFalse(resource.exists());
	}

	@Configuration
	@Import(GoogleStorageProtocolResolver.class)
	static class StorageApplication {

		@Bean
		public static Storage mockStorage() throws Exception {
			Storage storage = mock(Storage.class);
			BlobId validBlob = BlobId.of("test-spring", "images/spring.png");
			BlobId validBlobWithUnderscore = BlobId.of("test_spring", "images/spring.png");
			Bucket mockedBucket = mock(Bucket.class);
			Blob mockedBlob = mock(Blob.class);
			WriteChannel writeChannel = mock(WriteChannel.class);
			when(mockedBlob.exists()).thenReturn(true);
			when(mockedBucket.exists()).thenReturn(true);
			when(mockedBlob.getSize()).thenReturn(4096L);
			when(storage.get(eq(validBlob))).thenReturn(mockedBlob);
			when(storage.get(eq(validBlobWithUnderscore))).thenReturn(mockedBlob);
			when(storage.get("test-spring")).thenReturn(mockedBucket);
			when(storage.create(BucketInfo.newBuilder("non-existing").build())).thenReturn(mockedBucket);
			when(mockedBucket.getName()).thenReturn("test-spring");
			when(mockedBlob.writer()).thenReturn(writeChannel);
			return storage;
		}
	}

}
