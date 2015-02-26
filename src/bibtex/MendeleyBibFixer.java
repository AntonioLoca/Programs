package bibtex;

import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

/**
 * Reads an input BibTeX file generated by Mendeley (see mendeley.com) and creates a new BibTeX file including, for each
 * BibTeX entry, only the content that is interesting (according to a blacklist). Also, makes sure the title is the
 * first entry of the item and uses the URL as a comment above the BibTeX entry.
 * 
 * Possible improvements: - Replace Something(TM) with Something$\texttrademark$
 * 
 * @author Vitor E. Silva Souza (vitorsouza@gmail.com)
 * @version 1.0
 */
public class MendeleyBibFixer {
	/** Path to input file. */
	private static String BIB_FILE_INPUT_PATH = "mendeley.bib";

	/** Path to output file. */
	private static String BIB_FILE_OUTPUT_PATH = "mendeley-fix.bib";

	/** BibTeX keys we're not interested in. */
	private static String[] blacklist = new String[] { "annote", "abstract", "doi", "file", "issn", "keywords", "mendeley-tags", "month", "isbn", "address" };

	/** If ordinals should be put in overscript. */
	private static boolean doOverscript = true;

	/** Minimum amount of authors required to collapse the list from "X, Y, Z, ..." to "X et al."). */
	private static int collapseAuthorsCount = 7;

	/** Part of the ordinals to put in overscript. */
	private static final String[] overscript = new String[] { "st", "nd", "rd", "th" };

	/** Characters to un-escape. */
	private static final String[] escaped = new String[] { "\\\\\\&", "\\\\_", "\\\\%", "\\\\#", "\\\\~\\{\\}" };

	/** Un-escaped versions of above characters. */
	private static final String[] nonEscaped = new String[] { "&", "_", "%", "#", "~" };

	/** Strings that can start an URL. */
	private static final String[] urlStarters = new String[] { "http://", "https://", "ftp://" };

	/** Chars that can end an URL. */
	private static final char[] urlEnders = new char[] { ' ', ',', ')', '}' };

	/** BibTeX key for the publication title. */
	private static final String titleKey = "title";

	/** BibTeX key for the publication URL. */
	private static final String urlKey = "url";

	/** BibTeX key for the author. */
	private static final String authorKey = "author";

	/** BibTeX key for the editors. */
	private static final String editorKey = "editor";

	/** Main method. */
	public static void main(String[] args) throws Exception {
		// Checks for a configuration file and read the value of the constants from it.
		configure();

		File inFile = new File(BIB_FILE_INPUT_PATH);
		File outFile = new File(BIB_FILE_OUTPUT_PATH);

		// Initializes the objects needed for the parsing and output.
		Scanner in = new Scanner(inFile);
		PrintWriter out = new PrintWriter(outFile);
		StringBuilder builder = new StringBuilder();
		String titleLine = null, urlLine = null;

		// Parses all lines in the source file.
		while (in.hasNextLine()) {
			String line = in.nextLine();

			// Checks if it's the beginning of a new item.
			if (line.startsWith("@")) {
				builder.append(line).append('\n');

				// Prints a logging message.
				int idxA = line.indexOf('{'), idxB = line.indexOf(',');
				if ((idxA != -1) && (idxB > idxA)) {
					String bibKey = line.substring(idxA + 1, idxB);
					System.out.println("Processing: " + bibKey);
				}
			}

			// Checks if it's the end of an item. Prints the item to the output.
			else if (line.startsWith("}")) {
				// Finishes the builder and prints.
				builder.append('}').append('\n');
				printToOutput(builder, titleLine, urlLine, out);

				// Resets the variables.
				titleLine = null;
				urlLine = null;
				builder = new StringBuilder();
			}

			// Checks if it's the title line. Separates it so it can be the 1st line of the BibTeX item.
			else if (line.startsWith(titleKey)) titleLine = " " + encodeUrl(replaceOrdinals(line)) + "\n";

			// Checks if it's the author or editor lines. Checks if we should collapse the list.
			else if ((line.startsWith(authorKey)) || (line.startsWith(editorKey))) {
				int numAuthors = countAuthors(line);
				if (numAuthors > collapseAuthorsCount) line = collapseAuthors(line);
				builder.append(' ').append(replaceOrdinals(line)).append('\n');
			}

			// Checks if it's the URL line. Separates it so it can be the BibTeX item's comment.
			else if (line.startsWith(urlKey)) {
				int idx = line.indexOf('{');
				if (idx != -1) {
					int idxB = line.indexOf('}');
					urlLine = "% Source: " + replaceEscaped(line.substring(idx + 1, idxB));
				}
			}

			// Otherwise, check if the line is blacklisted and include in the output if it's not.
			else if (!isBlacklisted(line)) builder.append(' ').append(encodeUrl(replaceOrdinals(line))).append('\n');
		}

		// Closes everything.
		in.close();
		out.close();

		System.out.println("Done!");
	}

	/** If file bibfixer.properties is provided, change the value of the parameters with it. */
	private static void configure() throws Exception {
		File configFile = new File("bibfixer.properties");
		if (configFile.exists()) {
			Properties props = new Properties();
			props.load(new FileReader(configFile));

			String sInputFile = props.getProperty("input-file").trim();
			String sOutputFile = props.getProperty("output-file").trim();
			String sBlacklist = props.getProperty("blacklist").trim();
			String sDoOverscript = props.getProperty("do-overscript").trim();
			String sCollapseAuthorsCount = props.getProperty("collapse-authors-count").trim();

			if ((sInputFile != null) && (!sInputFile.isEmpty())) BIB_FILE_INPUT_PATH = sInputFile;
			if ((sOutputFile != null) && (!sOutputFile.isEmpty())) BIB_FILE_OUTPUT_PATH = sOutputFile;

			if ((sBlacklist != null) && (!sBlacklist.isEmpty())) {
				blacklist = sBlacklist.split("\\s*,\\s*");
			}

			if ((sDoOverscript != null) && (!sDoOverscript.isEmpty())) {
				if ("true".equals(sDoOverscript)) doOverscript = true;
				if ("false".equals(sDoOverscript)) doOverscript = false;
			}

			if ((sCollapseAuthorsCount != null) && (!sCollapseAuthorsCount.isEmpty())) {
				try {
					int cac = Integer.parseInt(sCollapseAuthorsCount);
					collapseAuthorsCount = cac;
				}
				catch (NumberFormatException e) {
					System.out.println("Invalid value for collapse-authors-count property (" + sCollapseAuthorsCount + "). Using default value: " + collapseAuthorsCount);
				}
			}
		}
	}

	/** Prints the BibTeX item to the output, fixing the title position and placing the URL as comment. */
	private static void printToOutput(StringBuilder builder, String titleLine, String urlLine, PrintWriter out) {
		int idx = builder.indexOf("\n");
		if (idx != -1) {
			// Adds the title as the first attribute of the entry.
			builder.insert(idx + 1, titleLine);

			// Removes the trailing comma, if any.
			int commaIdx = builder.length() - 4;
			if ((commaIdx > 0) && (builder.charAt(commaIdx) == ',')) builder.deleteCharAt(commaIdx);

			// Prints the URL (if any) as comment and then prints the BibTeX entry.
			if (urlLine != null) out.println(urlLine);
			out.println(builder.toString());
		}
	}

	/** Checks if the line refers to a BibTeX item that has been blacklisted (not interestint to us). */
	private static boolean isBlacklisted(String line) {
		for (String key : blacklist)
			if (line.startsWith(key)) return true;

		return false;
	}

	/** Replaces the ordinals with LaTeX code that puts the "st", "nd", "rd" or "th" in overscript. */
	private static String replaceOrdinals(String line) {
		if (doOverscript) {
			for (int i = 1; i < 4; i++)
				line = line.replaceAll(i + overscript[i - 1], i + "\\$^\\{\\\\rm " + overscript[i - 1] + "\\}\\$");
			for (int i = 1; i < 10; i++)
				line = line.replaceAll(i + overscript[3], i + "\\$^\\{\\\\rm " + overscript[3] + "\\}\\$");
		}
		return line;
	}

	/** Replaces escaped characters with their original form. */
	private static String replaceEscaped(String line) {
		for (int i = 0; i < escaped.length; i++)
			line = line.replaceAll(escaped[i], nonEscaped[i]);
		return line;
	}

	/** Counts the number of authors in the author line. */
	private static int countAuthors(String line) {
		boolean openQuote = false;
		boolean openBrackets = true;
		int authorCount = 0;
		int len = line.length();
		for (int i = 0; i < len; i++) {
			char c = line.charAt(i);
			if ((c == '"') && (i != 0) && (line.charAt(i - 1) != '\\')) openQuote = !openQuote;
			else if ((c == '{') || (c == '}')) openBrackets = !openBrackets;
			else if (!openQuote && !openBrackets && (c == 'a') && (i != 0) && (line.substring(i - 1).startsWith(" and "))) authorCount++;
		}
		return authorCount + 1;
	}

	/** Collapses the author/editor list to "X and others". */
	private static String collapseAuthors(String line) {
		boolean openQuote = false;
		boolean openBrackets = true;
		int andIdx = -1;
		int len = line.length();
		for (int i = 0; andIdx == -1 && i < len; i++) {
			char c = line.charAt(i);
			if ((c == '"') && (i != 0) && (line.charAt(i - 1) != '\\')) openQuote = !openQuote;
			else if ((c == '{') || (c == '}')) openBrackets = !openBrackets;
			else if (!openQuote && !openBrackets && (c == 'a') && (i != 0) && (line.substring(i - 1).startsWith(" and "))) andIdx = i + 3;
		}
		if (andIdx != -1) line = line.substring(0, andIdx) + " others},";
		return line;
	}

	/** Encodes URLs using LaTeX's url{} command. */
	private static String encodeUrl(String line) {
		line = replaceEscaped(line);
		Integer[] urlIdxs = findUrls(line);
		if (urlIdxs != null) for (int i = 0; i < urlIdxs.length; i++) {
			int a = urlIdxs[i], b = urlIdxs[++i];
			String url = line.substring(a, b);
			line = line.substring(0, a) + "\\url{" + line.substring(a, b) + "}" + line.substring(b);
		}
		return line;
	}

	/** Finds start and end indexes for URLs in the line. */
	private static Integer[] findUrls(String line) {
		List<Integer> idxs = new ArrayList<Integer>();

		for (String urlStarter : urlStarters) {
			int fromIdx = 0;
			while (true) {
				int idx = line.indexOf(urlStarter, fromIdx);
				if (idx == -1) break;
				idxs.add(idx);
				int endIdx = -1;
				for (char urlEnder : urlEnders) {
					int tmp = line.indexOf(urlEnder, idx);
					if ((tmp != -1) && ((endIdx == -1) || (tmp < endIdx))) endIdx = tmp;
				}
				if (endIdx == -1) endIdx = line.length();
				idxs.add(endIdx);
				fromIdx = endIdx;
			}
		}

		return (idxs.size() == 0) ? null : idxs.toArray(new Integer[] {});
	}
}
