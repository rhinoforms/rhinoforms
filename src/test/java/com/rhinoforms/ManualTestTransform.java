package com.rhinoforms;

import java.io.ByteArrayInputStream;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

public class ManualTestTransform {

	@Before
	public void setup() throws Exception {
		DocumentHelper documentHelper = new DocumentHelper();
		Document document = documentHelper.streamToDocument(new ByteArrayInputStream("<quote/>".getBytes()));
	}

	@Test
	public void test() {
		
	}
	
}
