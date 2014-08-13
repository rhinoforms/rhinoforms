package com.rhinoforms.flow;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class SubmissionTimeKeeperTest {

	private SubmissionTimeKeeper submissionTimeKeeper;
	private static final Logger LOGGER = LoggerFactory.getLogger(SubmissionTimeKeeperTest.class);

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

	@Test
	public void threadedTest() throws InterruptedException {
		for (int i = 0; i < 100; i++) {
			new Thread(new TimeSubmitter(submissionTimeKeeper)).start();
		}
		Thread.sleep(2000);
		Assert.assertEquals("[99]", submissionTimeKeeper.getEstimate("one", "next").toString());
	}

	private static List<Integer> list(int... i) {
		ArrayList<Integer> list = new ArrayList<>();
		for (int anInt : i) {
			list.add(anInt);
		}
		return list;
	}

	private static class TimeSubmitter implements Runnable {

		private SubmissionTimeKeeper tk;

		public TimeSubmitter(SubmissionTimeKeeper tk) {
			this.tk = tk;
		}

		@Override
		public void run() {
			try {
				for (int i = 0; i < 100; i++) {
					tk.recordTimeTaken("one", "next", list(100));
				}
			} catch (Exception e) {
				LOGGER.error(e.getMessage(), e);
			}
		}

	}

}
