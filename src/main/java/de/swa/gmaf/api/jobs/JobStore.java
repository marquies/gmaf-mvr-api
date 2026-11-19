package de.swa.gmaf.api.jobs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

/**
 * Keeps each job as a JSON file (&lt;id&gt;.json) in one folder, so job states
 * survive a restart.
 **/
class JobStore {
	private final File folder;
	private final Gson gson = new Gson();

	JobStore(File folder) {
		this.folder = folder;
		folder.mkdirs();
	}

	/** writes to a temp file first, so a reader never sees a half-written job */
	synchronized void save(ProcessingJob job) throws IOException {
		Path target = file(job.getId()).toPath();
		Path tmp = new File(folder, job.getId() + ".json.tmp").toPath();
		Files.write(tmp, gson.toJson(job).getBytes(StandardCharsets.UTF_8));
		Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	/** returns null for an unknown id */
	synchronized ProcessingJob load(String id) throws IOException {
		File f = file(id);
		if (!f.exists())
			return null;
		return gson.fromJson(Files.readString(f.toPath(), StandardCharsets.UTF_8), ProcessingJob.class);
	}

	synchronized List<ProcessingJob> loadAll() throws IOException {
		List<ProcessingJob> jobs = new ArrayList<ProcessingJob>();
		File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
		if (files != null)
			for (File f : files)
				jobs.add(gson.fromJson(Files.readString(f.toPath(), StandardCharsets.UTF_8), ProcessingJob.class));
		return jobs;
	}

	private File file(String id) {
		return new File(folder, id + ".json");
	}
}
