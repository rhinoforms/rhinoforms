<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
    <xsl:template match="serverData">
    	<submissionResult>
    		<premium>
        		<xsl:value-of select="wrapper/premium123" />
    		</premium>
    	</submissionResult>
    </xsl:template>
</xsl:stylesheet>