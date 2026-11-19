package de.swa.gmaf.api.jobs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.swa.gc.GraphCode;
import de.swa.gc.GraphCodeGenerator;
import de.swa.gc.GraphCodeIO;
import de.swa.gmaf.GMAF;
import de.swa.mmfg.MMFG;
import de.swa.mmfg.builder.FeatureVectorBuilder;
import de.swa.mmfg.builder.XMLEncodeDecode;
import de.swa.ui.Configuration;
import de.swa.ui.MMFGCollectionFactory;

/**
 * Processes assets in the background, so a caller does not have to keep an HTTP
 * request open until the analysis is done.
 * <p>
 * submit() stores the asset and returns a QUEUED job at once. A worker then
 * processes it (RUNNING) and ends it as DONE, with the MMFG id, or FAILED, with
 * the error. Job states are kept on disk. The stored asset, its .mmfg and its
 * .gc file are written to the same folders the collection loads at startup, so
 * results also survive a restart. Jobs that were not finished when the service
 * stopped are marked FAILED on the next start.
 **/
public class ProcessingJobs {
	private static final Logger logger = LoggerFactory.getLogger(ProcessingJobs.class);

	/** analyses an asset file */
	public interface Processor {
		MMFG process(File asset) throws Exception;
	}

	/** keeps a finished result */
	public interface ResultSink {
		void store(File asset, MMFG mmfg) throws Exception;
	}

	private final JobStore store;
	private final File assetFolder;
	private final ResultSink sink;
	private final ExecutorService workers;

	public ProcessingJobs(File jobFolder, File assetFolder, ResultSink sink, int threads) throws IOException {
		this.store = new JobStore(jobFolder);
		this.assetFolder = assetFolder;
		this.sink = sink;
		assetFolder.mkdirs();
		this.workers = Executors.newFixedThreadPool(threads, r -> {
			Thread t = new Thread(r, "gmaf-processing-job");
			t.setDaemon(true);
			return t;
		});
		failUnfinishedJobs();
	}

	/**
	 * stores the asset and queues it for processing; returns the QUEUED job
	 **/
	public ProcessingJob submit(byte[] bytes, String suffix, Processor processor) throws IOException {
		if (bytes == null || bytes.length == 0)
			throw new IllegalArgumentException("no asset data");
		String id = UUID.randomUUID().toString();
		File asset = new File(assetFolder, id + "." + cleanSuffix(suffix));
		Files.write(asset.toPath(), bytes);

		ProcessingJob job = new ProcessingJob(id, asset.getName());
		store.save(job);
		ProcessingJob queued = job.copy();
		workers.submit(() -> run(job, asset, processor));
		return queued;
	}

	/** returns null for an unknown or malformed id */
	public ProcessingJob get(String id) throws IOException {
		try {
			UUID.fromString(id); // also keeps ids like "../x" away from the file system
		} catch (IllegalArgumentException | NullPointerException x) {
			return null;
		}
		return store.load(id);
	}

	/** stops taking jobs and waits for the running ones (used by tests) */
	public void shutdown(long timeoutSeconds) throws InterruptedException {
		workers.shutdown();
		workers.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
	}

	private void run(ProcessingJob job, File asset, Processor processor) {
		try {
			job.setStatus(ProcessingJob.Status.RUNNING);
			store.save(job);

			MMFG mmfg = processor.process(asset);
			if (mmfg == null || mmfg.getId() == null)
				throw new IllegalStateException("processing returned no MMFG");
			sink.store(asset, mmfg);
			job.done(mmfg.getId().toString());
		} catch (Throwable x) {
			logger.error("Processing job " + job.getId() + " failed", x);
			job.failed(x.getMessage() != null ? x.getMessage() : x.getClass().getName());
		}
		try {
			store.save(job);
		} catch (IOException x) {
			logger.error("Could not save state of processing job " + job.getId(), x);
		}
	}

	private void failUnfinishedJobs() throws IOException {
		for (ProcessingJob job : store.loadAll()) {
			if (!job.getStatus().isFinished()) {
				job.failed("interrupted by a restart of the service");
				store.save(job);
			}
		}
	}

	/** keeps letters and digits only, so the suffix cannot leave the asset folder */
	static String cleanSuffix(String suffix) {
		String s = suffix == null ? "" : suffix.replaceAll("[^A-Za-z0-9]", "");
		return s.isEmpty() ? "bin" : s.toLowerCase();
	}

	// ---- service wiring ---------------------------------------------------

	private static ProcessingJobs instance;

	/**
	 * the service's instance: jobs in the folder "jobs", assets in the first
	 * collection path, results in the MMFG and GraphCode repositories and the
	 * shared collection
	 **/
	public static synchronized ProcessingJobs getInstance() throws IOException {
		if (instance == null) {
			Configuration c = Configuration.getInstance();
			int threads = Integer.getInteger("gmaf.jobs.threads", 2);
			instance = new ProcessingJobs(new File(System.getProperty("gmaf.jobs.folder", "jobs")),
					new File(c.getCollectionPaths().elementAt(0)),
					repositorySink(new File(c.getMMFGRepo()), new File(c.getGraphCodeRepository())), threads);
		}
		return instance;
	}

	/** processes with the given session, using its plugins */
	public static Processor with(GMAF session) {
		return asset -> session.processAsset(asset);
	}

	/**
	 * writes &lt;asset&gt;.mmfg and &lt;asset&gt;.gc, the files the collection
	 * reads at startup, and adds the MMFG to the shared collection
	 **/
	static ResultSink repositorySink(File mmfgRepo, File gcRepo) {
		return (asset, mmfg) -> {
			writeResult(asset, mmfg, mmfgRepo, gcRepo);
			// the asset name is new (job id), so the MMFG can just be added
			MMFGCollectionFactory.createOrGetCollection().addToCollection(mmfg);
		};
	}

	static void writeResult(File asset, MMFG mmfg, File mmfgRepo, File gcRepo) throws IOException {
		mmfgRepo.mkdirs();
		gcRepo.mkdirs();
		String xml = FeatureVectorBuilder.flatten(mmfg, new XMLEncodeDecode());
		Files.writeString(new File(mmfgRepo, asset.getName() + ".mmfg").toPath(), xml, StandardCharsets.UTF_8);
		GraphCode gc = GraphCodeGenerator.generate(mmfg);
		GraphCodeIO.write(gc, new File(gcRepo, asset.getName() + ".gc"));
	}
}
