package citibike;

import java.util.*;
import java.lang.*;

/* Name of the class has to be "Main" only if the class is public. */
public class Serialize {

	public static void main(String[] args) throws java.lang.Exception {
		Scanner sc = new Scanner(System.in);
		String first = sc.nextLine();
		String second = sc.nextLine();
		String third = sc.nextLine();
		String input = sc.nextLine();
		sc.close();
		Encryption e = new Encryption(first, second, third, input);
		e.encrypt();
		System.out.println(e);
	}
}

class Encryption {

	private String firstRow;
	private String secondRow;
	private String thirdRow;
	private String input;
	private int count;

	public Encryption(String firstRow, String secondRow, String thirdRow, String input) {
		this.firstRow = firstRow;
		this.secondRow = secondRow;
		this.thirdRow = thirdRow;
		this.input = input;
		count = 0;
	}

	public String moveRight(String str) {
		return str.substring(str.length() - 1) + str.substring(1);
	}

	public void encrypt() {
		for (int i = 0; i < input.length(); i++) {
			char char1 = input.charAt(i);
			int index1 = firstRow.indexOf(char1);
			System.out.println(firstRow);
			System.out.println(char1);
			char char2 = thirdRow.charAt(index1);
			int index2 = secondRow.indexOf(char2);
			char finalChar = thirdRow.charAt(index2);
			input = input.substring(0, i + 1) + finalChar + input.substring(i + 1);
			firstRow = moveRight(firstRow);
			count++;
			if (count == firstRow.length()) {
				secondRow = moveRight(secondRow);
				count = 0;
			}
		}
	}

	public String toString() {
		return input;
	}
}
