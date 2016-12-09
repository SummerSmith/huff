import java.util.PriorityQueue;

/**
 *	Interface that all compression suites must implement. That is they must be
 *	able to compress a file and also reverse/decompress that process.
 * 
 *	@author Brian Lavallee
 *	@since 5 November 2015
 *  @author Owen Atrachan
 *  @since December 1, 2016
 */
public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); // or 256
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;
	public static final int HUFF_COUNTS = HUFF_NUMBER | 2;

	public enum Header{TREE_HEADER, COUNT_HEADER};
	public Header myHeader = Header.TREE_HEADER;
	
	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		 int[] counts = readForCounts(in);
		    TreeNode root = makeTreeFromCounts(counts);
		    String[] codings = makeCodingsFromTree(root);
		    writeHeader(root,out);
		    in.reset();
		    writeCompressedBits(in,codings,out);
	}
	
	public int[] readForCounts(BitInputStream in) {
		int[] letters = new int[256];
		//does this initiate values to null or 0? if null, does this cause a problem in line 47?
		int on = in.readBits(BITS_PER_WORD);
		//how do we know when to stop reading the file? When we have reached the last character? also in line 115
		while (on != -1) {
			letters[on] = letters[on] + 1;
			on = in.readBits(BITS_PER_WORD);
		}
		return letters;
	}
	
	public TreeNode makeTreeFromCounts(int [] letters) {
		PriorityQueue<TreeNode> occurences = new PriorityQueue<>();
		for(int i = 0; i<letters.length; i++){
			if (letters[i] > 0) {
				TreeNode temp = new TreeNode(i, letters[i], null, null);
				occurences.add(temp);
			}
		}
		occurences.add(new TreeNode(PSEUDO_EOF, 1, null, null));
		while (occurences.size() >= 2) {
			TreeNode left = occurences.remove();
			TreeNode right = occurences.remove();
			//0 was changed to -1, myValue was changed to myWeight
			occurences.add(new TreeNode(-1, left.myWeight + right.myWeight, left, right));
		}
		return occurences.poll();
	}
	
	public String[] makeCodingsFromTree(TreeNode current) {
		String[] codings = new String[257];
		findCode(current, codings, "");
		return codings;
	}
	
	public void findCode(TreeNode current, String[] codings, String code) {
		if(current == null) {
			return;
		}
		if(current.myLeft == null && current.myRight == null) {
			codings[current.myValue] = code;
			return;
		}
		findCode(current.myLeft, codings, code + "0");
		findCode(current.myRight, codings, code + "1");
	}
	
	public void writeHeader(TreeNode current, BitOutputStream out) {
		out.writeBits(32, HUFF_NUMBER);
		traversal(current, out);
	}
	
	public void traversal(TreeNode current, BitOutputStream out) {
		if(current == null) {
			return;
		}
		if (current.myLeft == null && current.myRight == null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, current.myValue);
		}
		else {
			out.writeBits(1, 0);
			traversal(current.myLeft, out);
			traversal(current.myRight, out);
		}			
	}
	
	public void writeCompressedBits(BitInputStream in, String[] encodings, BitOutputStream out) {
		int on = in.readBits(BITS_PER_WORD);
		while (on != -1) {
//			for(int i = 0; i<encodings[on].length(); i++) {
//				out.writeBits(1, encodings[on].charAt(i));
//			}
			if(encodings[on] == null) {
				throw new HuffException("error in writeCompressedBits");
			}
			out.writeBits(encodings[on].length(), Integer.parseInt(encodings[on],2));
			on = in.readBits(BITS_PER_WORD);
		}
		out.writeBits(encodings[PSEUDO_EOF].length(), Integer.parseInt(encodings[PSEUDO_EOF],2));
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
            int val = in.readBits(BITS_PER_INT);
            if(val != HUFF_NUMBER && val != HUFF_TREE) {
            	throw new HuffException("value not valid");
            }
            TreeNode root = readTreeHeader(in);
            readCompressedBits(in, out, root);
	}
	
	public TreeNode readTreeHeader(BitInputStream in) {
		int on = in.readBits(1);
		if (on == 0) {
			TreeNode left = readTreeHeader(in);
			TreeNode right = readTreeHeader(in);
			return new TreeNode(-1,-1,left,right);
		}
		on = in.readBits(BITS_PER_WORD + 1);
		TreeNode current = new TreeNode(on, -1, null, null);
		return current;
	}
	
	public void readCompressedBits(BitInputStream in, BitOutputStream out, TreeNode root) {
		TreeNode temp = root;
		while (true) {
			int on = in.readBits(1);
			if (on == 0) {
				temp = temp.myLeft;
			}
			else {
				temp = temp.myRight;
			}
			if(temp.myLeft == null && temp.myRight == null) {
				if(temp.myValue == PSEUDO_EOF) {
					break;
				}
				out.writeBits(BITS_PER_WORD, temp.myValue);
				temp = root;
			}
		}
	}
	
	public void setHeader(Header header) {
        myHeader = header;
        System.out.println("header set to "+myHeader);
    }
}