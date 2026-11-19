package de.swa.gmaf.api.jobs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.swa.mmfg.MMFG;
import de.swa.mmfg.Node;
import de.swa.mmfg.builder.FeatureVectorBuilder;
import de.swa.mmfg.builder.XMLEncodeDecode;

class ProcessingJobsTest {

	@TempDir
	File dir;

	private final List<File> stored = Collections.synchronizedList(new ArrayList<File>());

	private ProcessingJobs jobs() throws Exception {
		return new ProcessingJobs(new File(dir, "jobs"), new File(dir, "assets"), (asset, mmfg) -> stored.add(asset), 2);
	}

	private static MMFG mmfg() {
		MMFG m = new MMFG();
		Node n = new Node("Root-Asset", m);
		m.addNode(n);
		return m;
	}

	private static ProcessingJob await(ProcessingJobs jobs, String id) throws Exception {
		for (int i = 0; i < 200; i++) {
			ProcessingJob job = jobs.get(id);
			if (job.getStatus().isFinished())
				return job;
			Thread.sleep(25);
		}
		throw new AssertionError("job " + id + " did not finish");
	}

	@Test
	void submit_returnsAQueuedJobAtOnce_whileProcessingStillRuns() throws Exception {
		ProcessingJobs jobs = jobs();
		CountDownLatch release = new CountDownLatch(1);

		ProcessingJob job = jobs.submit(new byte[] { 1 }, "jpg", asset -> {
			release.await(5, TimeUnit.SECONDS);
			return mmfg();
		});

		assertEquals(ProcessingJob.Status.QUEUED, job.getStatus());
		ProcessingJob.Status now = jobs.get(job.getId()).getStatus();
		assertTrue(now == ProcessingJob.Status.QUEUED || now == ProcessingJob.Status.RUNNING, now.toString());
		release.countDown();
		assertEquals(ProcessingJob.Status.DONE, await(jobs, job.getId()).getStatus());
	}

	@Test
	void submit_returnsTheQueuedState_evenWhenTheWorkerHasAlreadyStarted() throws Exception {
		ProcessingJobs jobs = jobs();
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		ProcessingJob job = jobs.submit(new byte[] { 1 }, "jpg", asset -> {
			started.countDown();
			release.await(5, TimeUnit.SECONDS);
			return mmfg();
		});
		started.await(5, TimeUnit.SECONDS);

		assertEquals(ProcessingJob.Status.QUEUED, job.getStatus());
		release.countDown();
		await(jobs, job.getId());
	}

	@Test
	void finishedJob_isDoneWithTheMmfgId_andTheResultIsStored() throws Exception {
		ProcessingJobs jobs = jobs();
		MMFG m = mmfg();

		ProcessingJob job = await(jobs, jobs.submit(new byte[] { 1, 2, 3 }, "png", asset -> m).getId());

		assertEquals(ProcessingJob.Status.DONE, job.getStatus());
		assertEquals(m.getId().toString(), job.getMmfgId());
		assertNull(job.getError());
		assertEquals(1, stored.size());
		assertEquals(job.getAssetName(), stored.get(0).getName());
	}

	@Test
	void asset_isStoredUnderTheJobIdWithItsSuffix() throws Exception {
		ProcessingJobs jobs = jobs();
		byte[] bytes = { 4, 5, 6 };

		ProcessingJob job = await(jobs, jobs.submit(bytes, "JPG", asset -> mmfg()).getId());

		File asset = new File(new File(dir, "assets"), job.getId() + ".jpg");
		assertEquals(asset.getName(), job.getAssetName());
		assertArrayEquals(bytes, Files.readAllBytes(asset.toPath()));
	}

	@Test
	void failingProcessing_endsAsFailedWithTheError() throws Exception {
		ProcessingJobs jobs = jobs();

		ProcessingJob job = await(jobs, jobs.submit(new byte[] { 1 }, "jpg", asset -> {
			throw new IllegalStateException("plugin broke");
		}).getId());

		assertEquals(ProcessingJob.Status.FAILED, job.getStatus());
		assertEquals("plugin broke", job.getError());
		assertNull(job.getMmfgId());
		assertTrue(stored.isEmpty());
	}

	@Test
	void finishedJobs_canBeReadAfterARestart() throws Exception {
		ProcessingJobs before = jobs();
		String id = await(before, before.submit(new byte[] { 1 }, "jpg", asset -> mmfg()).getId()).getId();
		before.shutdown(5);

		ProcessingJob job = jobs().get(id);

		assertNotNull(job);
		assertEquals(ProcessingJob.Status.DONE, job.getStatus());
	}

	@Test
	void unfinishedJobs_areMarkedFailedOnTheNextStart() throws Exception {
		ProcessingJobs before = jobs();
		CountDownLatch never = new CountDownLatch(1);
		String id = before.submit(new byte[] { 1 }, "jpg", asset -> {
			never.await(10, TimeUnit.SECONDS);
			return mmfg();
		}).getId();

		ProcessingJob job = jobs().get(id); // a new instance on the same folders, as after a restart

		assertEquals(ProcessingJob.Status.FAILED, job.getStatus());
		assertTrue(job.getError().contains("restart"));
		never.countDown();
		before.shutdown(5);
	}

	@Test
	void unknownOrMalformedIds_returnNull() throws Exception {
		ProcessingJobs jobs = jobs();
		assertNull(jobs.get("2b0b8d4e-0000-4000-8000-000000000000"));
		assertNull(jobs.get("../jobs/x"));
		assertNull(jobs.get(null));
	}

	@Test
	void emptyAsset_isRejected() throws Exception {
		ProcessingJobs jobs = jobs();
		assertThrows(IllegalArgumentException.class, () -> jobs.submit(new byte[0], "jpg", asset -> mmfg()));
	}

	@Test
	void suffix_isReducedToLettersAndDigits() {
		assertEquals("jpg", ProcessingJobs.cleanSuffix(".jpg"));
		assertEquals("etcpasswd", ProcessingJobs.cleanSuffix("/../etc/passwd"));
		assertEquals("bin", ProcessingJobs.cleanSuffix(null));
		assertEquals("bin", ProcessingJobs.cleanSuffix("../"));
	}

	@Test
	void writeResult_writesAnMmfgFileTheCollectionCanLoadAgain() throws Exception {
		File asset = new File(dir, "a.jpg");
		MMFG m = mmfg();

		ProcessingJobs.writeResult(asset, m, new File(dir, "mmfgs"), new File(dir, "graphcodes"));

		File mmfgFile = new File(dir, "mmfgs/a.jpg.mmfg");
		assertTrue(new File(dir, "graphcodes/a.jpg.gc").exists());
		MMFG loaded = FeatureVectorBuilder.unflatten(Files.readString(mmfgFile.toPath()), new XMLEncodeDecode());
		assertEquals(m.getId(), loaded.getId());
	}
}
