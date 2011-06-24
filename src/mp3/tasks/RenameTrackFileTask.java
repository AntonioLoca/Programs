package mp3.tasks;

import java.io.File;

import mp3.domain.Track;

import org.apache.log4j.Logger;

public class RenameTrackFileTask extends RenameTask {
	private static final Logger log = Logger.getLogger(RenameTrackFileTask.class);

	private Track track;

	public RenameTrackFileTask(String newName, Track track) {
		super(newName);
		this.track = track;
	}

	@Override
	protected int weight() {
		// Tracks should be renamed first.
		return 1;
	}

	@Override
	public int compareTo(Task o) {
		// If not the same kind of rename task, use the superclass' implementation.
		if (! (o instanceof RenameTrackFileTask)) return super.compareTo(o);
		
		// If it's the same, compare by track. There shouldn't be two tasks for the same track.
		else return track.compareTo(((RenameTrackFileTask)o).track);
	}

	@Override
	public String toString() {
		return "Track rename: \"" + track.getName() + "\" -> \"" + newName + "\"";
	}

	@Override
	public void execute() {
		track.setName(newName);
		File file = track.getFile();
		File newFile = new File(file.getParent(), "" + track + ".mp3");
		log.info("Renaming: [" + file.getName() + "] -> [" + newFile.getName() + "]");
		file.renameTo(newFile);
	}
}
