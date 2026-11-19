package de.swa.gmaf.api.jobs;

import java.time.Instant;

/**
 * State of one asynchronous processing job, as stored on disk and returned by
 * the REST API.
 **/
public class ProcessingJob {

	public enum Status {
		QUEUED, RUNNING, DONE, FAILED;

		public boolean isFinished() {
			return this == DONE || this == FAILED;
		}
	}

	private String id;
	private Status status;
	/** name of the stored asset file, also used for its .mmfg and .gc files */
	private String assetName;
	/** set when the job is DONE */
	private String mmfgId;
	/** set when the job FAILED */
	private String error;
	private String created;
	private String updated;

	ProcessingJob() {
	}

	ProcessingJob(String id, String assetName) {
		this.id = id;
		this.assetName = assetName;
		this.created = Instant.now().toString();
		setStatus(Status.QUEUED);
	}

	/** a copy, so a caller sees a fixed state while a worker goes on updating the job */
	ProcessingJob copy() {
		ProcessingJob c = new ProcessingJob();
		c.id = id;
		c.status = status;
		c.assetName = assetName;
		c.mmfgId = mmfgId;
		c.error = error;
		c.created = created;
		c.updated = updated;
		return c;
	}

	void setStatus(Status status) {
		this.status = status;
		this.updated = Instant.now().toString();
	}

	void done(String mmfgId) {
		this.mmfgId = mmfgId;
		setStatus(Status.DONE);
	}

	void failed(String error) {
		this.error = error;
		setStatus(Status.FAILED);
	}

	public String getId() {
		return id;
	}

	public Status getStatus() {
		return status;
	}

	public String getAssetName() {
		return assetName;
	}

	public String getMmfgId() {
		return mmfgId;
	}

	public String getError() {
		return error;
	}

	public String getCreated() {
		return created;
	}

	public String getUpdated() {
		return updated;
	}
}
