package com.rhinoforms.flow;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SubmissionTimeKeeperTest {

	private SubmissionTimeKeeper submissionTimeKeeper;

	@Before
	public void setup() {
		submissionTimeKeeper = new SubmissionTimeKeeper();
	}
	
	@Test
	public void test() {
		submissionTimeKeeper.recordTimeTaken("one", "next", list(300));
		submissionTimeKeeper.recordTimeTaken("two", "next", list(100, 10000));
		
		submissionTimeKeeper.recordTimeTaken("one", "next", list(600));
		submissionTimeKeeper.recordTimeTaken("two", "next", list(100, 8000));

		submissionTimeKeeper.recordTimeTaken("one", "next", list(600));
		submissionTimeKeeper.recordTimeTaken("two", "next", list(100, 9000));
		
		Assert.assertEquals("[466]", submissionTimeKeeper.getEstimate("one", "next").toString());
		Assert.assertEquals("[99, 9220]", submissionTimeKeeper.getEstimate("two", "next").toString());
	}

	private List<Integer> list(int... i) {
		ArrayList<Integer> list = new ArrayList<Integer>();
		for (int anInt : i) {
			list.add(anInt);
		}
		return list;
	}
	
}
