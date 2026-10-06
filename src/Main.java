import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Main {
    // Stores Myers diff result
    // ' ' = same, '-' = delete, '+' = insert
    static class Script {
        byte[] type;
        int[] index;
        int size;
    }
    public static void main(String[] args) throws IOException {
        if (args.length != 3 || !(args[0].equals("lines") || args[0].equals("highlight"))) {
            System.err.println("usage: Main lines|highlight A_PATH B_PATH");
            System.exit(2);
            return;
        }
        byte[] oldFile;
        byte[] newFile;

        try {
            oldFile = Files.readAllBytes(Path.of(args[1]));
            newFile = Files.readAllBytes(Path.of(args[2]));

        } catch (IOException | RuntimeException e) {
            // Required by assignment:
            // missing/invalid input -> exit code 2
            System.err.println("Error reading file: " + e.getMessage());
            System.exit(2);
            return;
        }
        // 3. Convert files into lines
        List<byte[]> oldLines = splitLines(oldFile);
        List<byte[]> newLines = splitLines(newFile);

        // Give every different line an integer ID.
        // Same lines get the same ID.
        HashMap<String, Integer> ids = new HashMap<>();

        int[] a = makeIds(oldLines, ids);
        int[] b = makeIds(newLines, ids);

        // 4. Part A: Myers line diff
        Script script = myers(a, b);

        // Put '-' before '+' in every change block
        normalize(script);
        OutputStream out = new BufferedOutputStream(System.out);
        boolean highlight = args[0].equals("highlight");

        int i = 0;
        // 5. Print line diff
        while (i < script.size) {
            // Same line
            if (script.type[i] == ' ') {
                writeLine(out, ' ',oldLines.get(script.index[i]));
                i++;
                continue;
            }
            // Find end of current change block
            int j = i;
            while (j < script.size && script.type[j] != ' ') {
                j++;
            }
            // Find deleted lines
            int deleteEnd = i;
            while (deleteEnd < j && script.type[deleteEnd] == '-') {
                deleteEnd++;
            }
            int deleteCount = deleteEnd - i;
            int insertCount = j - deleteEnd;
            // Print deleted lines
            for (int k = i; k < deleteEnd; k++) {
                writeLine(out,'-', oldLines.get(script.index[k]));
            }
            // Print inserted lines
            for (int k = 0; k < insertCount; k++) {
                byte[] newLine =newLines.get(script.index[deleteEnd + k]);
                writeLine(out, '+', newLine);

                // Part B: character-level highlight
                if (highlight && k < deleteCount) {
                    byte[] oldLine = oldLines.get(script.index[i + k]);
                    String highlightResult = makeHighlight(oldLine,newLine);
                    out.write(highlightResult.getBytes(StandardCharsets.UTF_8));
                    out.write('\n');
                }
            }
            i = j;
        }
        out.flush();
    }
    // Write one diff line
    static void writeLine(OutputStream out,char type,byte[] line) throws IOException {
        out.write(type);
        out.write(line);
        out.write('\n');
    }
    // Split file into lines
    static List<byte[]> splitLines(byte[] data) {
        List<byte[]> lines = new ArrayList<>();
        int start = 0;

        for (int i = 0; i < data.length; i++) {
            if (data[i] == '\n') {
                lines.add(Arrays.copyOfRange(data,start,i));
                start = i + 1;
            }
        }
        // Add final line if it exists
        if (start < data.length) {
            lines.add(Arrays.copyOfRange(data,start,data.length));
        }
        return lines;
    }
    // Convert every unique line into an integer ID
    static int[] makeIds(List<byte[]> lines,HashMap<String, Integer> ids) {
        int[] result = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            String line =new String(lines.get(i),StandardCharsets.ISO_8859_1);
            Integer id = ids.get(line);
            if (id == null) {
                id = ids.size();
                ids.put(line, id);
            }
            result[i] = id;
        }

        return result;
    }
    // Normalize:
    // Put all '-' before all '+'
    static void normalize(Script s) {
        int i = 0;
        while (i < s.size) {
            // Matching line
            if (s.type[i] == ' ') {
                i++;
                continue;
            }
            // Find complete change block
            int j = i;
            while (j < s.size &&
                   s.type[j] != ' ') {
                j++;
            }
            // Save current block
            byte[] types = Arrays.copyOfRange(s.type,i,j);
            int[] indexes = Arrays.copyOfRange(s.index,i,j);
            int pos = i;

            // First deletions
            for (int k = 0; k < types.length; k++) {
                if (types[k] == '-') {
                    s.type[pos] = '-';
                    s.index[pos] = indexes[k];
                    pos++;
                }
            }
            // Then insertions
            for (int k = 0; k < types.length; k++) {
                if (types[k] == '+') {
                    s.type[pos] = '+';
                    s.index[pos] = indexes[k];
                    pos++;
                }
            }
            i = j;
        }
    }
    // MYERS O(ND) DIFF
    static Script myers(int[] a, int[] b) {
        int n = a.length;
        int m = b.length;

        int max = n + m;

        Script result = new Script();

        result.type = new byte[max];
        result.index = new int[max];
        result.size = 0;

        // Both sequences empty
        if (max == 0) {
            return result;
        }
        /*k = x - y ,v[k] stores the farthest x reached on diagonal k.*/

        int offset = max;
        int[] v = new int[2 * max + 2];

        // Store V for every D.
        ArrayList<int[]> trace = new ArrayList<>();
        int finalD = -1;

        // Forward phase
        for (int d = 0; d <= max; d++) {
            for (int k = -d; k <= d; k += 2) {
                int x;
                if (k == -d || (k != d && v[offset + k - 1] <v[offset + k + 1])) {
                    x = v[offset + k + 1];

                } else {
                    x =v[offset + k - 1] + 1;
                }
                int y = x - k;
                while (x < n && y < m && a[x] == b[y]) {
                    x++;
                    y++;
                }
                // Store farthest x
                v[offset + k] = x;
                // Reached end of both sequences
                if (x >= n && y >= m) {
                    finalD = d;
                    break;
                }
            }

            if (finalD != -1) {
                break;
            }
            // Save V for backtracking
            int[] snapshot = new int[d + 1];

            for (int i = 0; i <= d; i++) {
                snapshot[i] = v[offset - d + 2 * i];
            }
            trace.add(snapshot);
        }
        // Backtracking
        int x = n;
        int y = m;
        int pos = 0;

        for (int d = finalD; d > 0; d--) {
            int[] previous = trace.get(d - 1);
            int previousD = d - 1;
            int k = x - y;
            int previousK;

            if (k == -d ||(k != d && previous[(k - 1 + previousD) / 2]<previous[(k + 1 + previousD) / 2])) {
                previousK = k + 1;
            } else {
                previousK = k - 1;
            }
            int previousX = previous[(previousK + previousD) / 2];
            int previousY = previousX - previousK;
            // Matching part / snake
            while (x > previousX && y > previousY) {
                x--;
                y--;

                result.type[pos] = ' ';
                result.index[pos] = x;

                pos++;
            }
            // Insertion
            if (x == previousX) {
                y--;
                result.type[pos] = '+';
                result.index[pos] = y;
            }
            // Deletion
            else {
                x--;
                result.type[pos] = '-';
                result.index[pos] = x;
            }
            pos++;
        }
        // Remaining matching part
        while (x > 0 && y > 0) {
            x--;
            y--;
            result.type[pos] = ' ';
            result.index[pos] = x;
            pos++;
        }
        // We built result backwards.
        // Reverse it.
        for (int left = 0, right = pos - 1; left < right; left++, right--){
            byte tempType = result.type[left];
            result.type[left] = result.type[right];
            result.type[right] = tempType;
            int tempIndex = result.index[left];

            result.index[left] = result.index[right];
            result.index[right] =tempIndex;
        }

        result.size = pos;
        return result;
    }
    // PART B
    // Character-level diff
    static String makeHighlight(byte[] oldLine,byte[] newLine) {
        /* Convert lines into Unicode code points. Myers will now compare characters instead of complete lines.*/
        int[] oldChars =new String(oldLine,StandardCharsets.UTF_8).codePoints().toArray();
        int[] newChars =new String(newLine,StandardCharsets.UTF_8).codePoints().toArray();

        // Same Myers algorithm again,
        // but this time on characters.
        Script script = myers(oldChars, newChars);
        StringBuilder oldRanges = new StringBuilder();
        StringBuilder newRanges = new StringBuilder();

        int oldPos = 0;
        int newPos = 0;
        int oldStart = -1;
        int newStart = -1;

        // Find changed character ranges
        for (int i = 0;i < script.size;i++) {
            char type =(char) script.type[i];
            // Same character
            if (type == ' ') {
                if (oldStart != -1) {
                    appendRange(oldRanges,oldStart,oldPos);
                    oldStart = -1;
                }
                if (newStart != -1) {
                    appendRange(newRanges,newStart,newPos);
                    newStart = -1;
                }
                oldPos++;
                newPos++;
            }
            // Deleted character
            else if (type == '-') {
                if (oldStart == -1) {
                    oldStart = oldPos;
                }
                oldPos++;
            }
            // Inserted character
            else {
                if (newStart == -1) {
                    newStart = newPos;
                }
                newPos++;
            }
        }
        // Close last range
        if (oldStart != -1) {
            appendRange(oldRanges,oldStart,oldPos);
        }

        if (newStart != -1) {
            appendRange(newRanges,newStart,newPos);
        }

        String oldResult = oldRanges.length() == 0 ? ".": oldRanges.toString();
        String newResult = newRanges.length() == 0 ? ".": newRanges.toString();

        return "? " +oldResult +" | " + newResult;
    }
    // Add one changed range
    static void appendRange(StringBuilder result,int start,int end){   
        if (result.length() > 0) {
            result.append(',');
        }
        result.append(start).append('-').append(end);
    }
}