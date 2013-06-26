<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:template match="/myData">
    	<myData>
	    	<outerNode>
	    		<copy>
	        		<xsl:value-of select="toCopy" />
	    		</copy>
	    		<new>Post transform addition</new>
	    	</outerNode>
    	</myData>
    </xsl:template>
</xsl:stylesheet>