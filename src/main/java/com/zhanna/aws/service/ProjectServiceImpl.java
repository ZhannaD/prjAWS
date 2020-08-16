package com.zhanna.aws.service;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;

import com.zhanna.aws.client.AWS3Client;
import com.zhanna.aws.client.AWSClientFactory;
import com.zhanna.aws.client.ClientType;
import com.zhanna.aws.common.Constants;
import com.zhanna.aws.exception.UploadFailedException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

@Component
@Slf4j
public class ProjectServiceImpl implements ProjectService {

	@Autowired
	AWSClientFactory clientFactory;

	/**
	 * Save file using a multipart upload. This method does not require any
	 * temporary storage at the REST service
	 * 
	 * @param headers
	 * @param bucket  Bucket name
	 * @param part    Uploaded file
	 * @return
	 */
	public Mono<String> saveFile(HttpHeaders headers, FilePart part, String folderName) {

		// Generate a filekey for this upload
		String filekey = folderName + Constants.SEPARATE_FOLDER + UUID.randomUUID().toString();

		log.info("[I137] saveFile: filekey={}, filename={}", filekey, part.filename());

		// Gather metadata
		Map<String, String> metadata = new HashMap<String, String>();
		String filename = part.filename();
		if (filename == null) {
			filename = filekey;
		}

		metadata.put("filename", filename);

		MediaType mt = part.headers().getContentType();
		if (mt == null) {
			mt = MediaType.APPLICATION_OCTET_STREAM;
		}

		AWS3Client client = (AWS3Client) clientFactory.getAWSClient(ClientType.S3CLIENT);
		// Create multipart upload request
		CompletableFuture<CreateMultipartUploadResponse> uploadRequest = client.getS3client().createMultipartUpload(CreateMultipartUploadRequest
				.builder().contentType(mt.toString()).key(filekey).metadata(metadata).bucket(client.getS3config().getBucket()).build());

		// This variable will hold the upload state that we must keep
		// around until all uploads complete
		final UploadState uploadState = new UploadState(client.getS3config().getBucket(), filekey);

		return Mono.fromFuture(uploadRequest).flatMapMany((response) -> {
			checkResult(response);
			uploadState.uploadId = response.uploadId();
			log.info("[I183] uploadId={}", response.uploadId());
			return part.content();
		}).bufferUntil((buffer) -> {
			uploadState.buffered += buffer.readableByteCount();
			if (uploadState.buffered >= client.getS3config().getMultipartMinPartSize()) {
				log.info("[I173] bufferUntil: returning true, bufferedBytes={}, partCounter={}, uploadId={}", uploadState.buffered,
						uploadState.partCounter, uploadState.uploadId);
				uploadState.buffered = 0;
				return true;
			} else {
				return false;
			}
		}).map((buffers) -> concatBuffers(buffers)).flatMap((buffer) -> uploadPart(uploadState, buffer, client.getS3client())).onBackpressureBuffer()
				.reduce(uploadState, (state, completedPart) -> {
					log.info("[I188] completed: partNumber={}, etag={}", completedPart.partNumber(), completedPart.eTag());
					state.completedParts.put(completedPart.partNumber(), completedPart);
					return state;
				}).flatMap((state) -> completeUpload(state, client.getS3client())).map((response) -> {
					checkResult(response);
					return uploadState.filekey;
				});
	}

	/**
	 * Multipart file upload
	 * 
	 * @param bucket
	 * @param parts
	 * @param headers
	 * @return
	 */

	private static ByteBuffer concatBuffers(List<DataBuffer> buffers) {
		log.info("[I198] creating BytBuffer from {} chunks", buffers.size());

		int partSize = 0;
		for (DataBuffer b : buffers) {
			partSize += b.readableByteCount();
		}

		ByteBuffer partData = ByteBuffer.allocate(partSize);
		buffers.forEach((buffer) -> {
			partData.put(buffer.asByteBuffer());
		});

		// Reset read pointer to first byte
		partData.rewind();

		log.info("[I208] partData: size={}", partData.capacity());
		return partData;

	}

	/**
	 * Upload a single file part to the requested bucket
	 * 
	 * @param uploadState
	 * @param buffer
	 * @return
	 */
	private Mono<CompletedPart> uploadPart(UploadState uploadState, ByteBuffer buffer, S3AsyncClient s3client) {
		final int partNumber = ++uploadState.partCounter;
		log.info("[I218] uploadPart: partNumber={}, contentLength={}", partNumber, buffer.capacity());

		CompletableFuture<UploadPartResponse> request = s3client
				.uploadPart(
						UploadPartRequest.builder().bucket(uploadState.bucket).key(uploadState.filekey).partNumber(partNumber)
								.uploadId(uploadState.uploadId).contentLength((long) buffer.capacity()).build(),
						AsyncRequestBody.fromPublisher(Mono.just(buffer)));

		return Mono.fromFuture(request).map((uploadPartResult) -> {
			checkResult(uploadPartResult);
			log.info("[I230] uploadPart complete: part={}, etag={}", partNumber, uploadPartResult.eTag());
			return CompletedPart.builder().eTag(uploadPartResult.eTag()).partNumber(partNumber).build();
		});
	}

	private Mono<CompleteMultipartUploadResponse> completeUpload(UploadState state, S3AsyncClient s3client) {
		log.info("[I202] completeUpload: bucket={}, filekey={}, completedParts.size={}", state.bucket, state.filekey, state.completedParts.size());

		CompletedMultipartUpload multipartUpload = CompletedMultipartUpload.builder().parts(state.completedParts.values()).build();

		return Mono.fromFuture(s3client.completeMultipartUpload(CompleteMultipartUploadRequest.builder().bucket(state.bucket).uploadId(state.uploadId)
				.multipartUpload(multipartUpload).key(state.filekey).build()));
	}

	/**
	 * check result from an API call.
	 * 
	 * @param result Result from an API call
	 */
	private static void checkResult(SdkResponse result) {
		if (result.sdkHttpResponse() == null || !result.sdkHttpResponse().isSuccessful()) {
			throw new UploadFailedException(result);
		}
	}

	/**
	 * Holds upload state during a multipart upload
	 */
	static class UploadState {
		final String bucket;
		final String filekey;

		String uploadId;
		int partCounter;
		Map<Integer, CompletedPart> completedParts = new HashMap<>();
		int buffered = 0;

		UploadState(String bucket, String filekey) {
			this.bucket = bucket;
			this.filekey = filekey;
		}
	}
}
