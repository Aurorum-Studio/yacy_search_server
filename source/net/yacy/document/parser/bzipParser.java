//bzipParser.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.document.parser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Idiom;
import net.yacy.document.TextParser;
import net.yacy.document.ParserException;
import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.util.FileUtils;

import org.apache.tools.bzip2.CBZip2InputStream;


public class bzipParser extends AbstractParser implements Idiom {

    /**
     * a list of mime types that are supported by this parser class
     * @see #getSupportedMimeTypes()
     */
    public static final Set<String> SUPPORTED_MIME_TYPES = new HashSet<String>();
    public static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>();
    static {
        SUPPORTED_EXTENSIONS.add("bz2");
        SUPPORTED_EXTENSIONS.add("tbz");
        SUPPORTED_EXTENSIONS.add("tbz2");
        SUPPORTED_MIME_TYPES.add("application/x-bzip2");
        SUPPORTED_MIME_TYPES.add("application/bzip2");
        SUPPORTED_MIME_TYPES.add("application/x-bz2");
        SUPPORTED_MIME_TYPES.add("application/x-bzip");
        SUPPORTED_MIME_TYPES.add("application/x-stuffit");
    }
    
    public bzipParser() {        
        super("Bzip 2 UNIX Compressed File Parser");
    }
    
    public Set<String> supportedMimeTypes() {
        return SUPPORTED_MIME_TYPES;
    }
    
    public Set<String> supportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }
    
    public Document parse(final DigestURI location, final String mimeType, final String charset, final InputStream source) throws ParserException, InterruptedException {
        
        File tempFile = null;
        try {           
            /*
             * First we have to consume the first two char from the stream. Otherwise
             * the bzip decompression will fail with a nullpointerException!
             */
            int b = source.read();
            if (b != 'B') {
                throw new Exception("Invalid bz2 content.");
            }
            b = source.read();
            if (b != 'Z') {
                throw new Exception("Invalid bz2 content.");
            }           
            
            int read = 0;
            final byte[] data = new byte[1024];
            final CBZip2InputStream zippedContent = new CBZip2InputStream(source);        
            
            tempFile = File.createTempFile("bunzip","tmp");
            tempFile.deleteOnExit();
            
            // creating a temp file to store the uncompressed data
            final FileOutputStream out = new FileOutputStream(tempFile);
            
            // reading gzip file and store it uncompressed
            while((read = zippedContent.read(data, 0, 1024)) != -1) {
                out.write(data, 0, read);
            }
            zippedContent.close();
            out.close();
             
            // check for interruption
            checkInterruption();
            
            // creating a new parser class to parse the unzipped content
            return TextParser.parseSource(location,null,null,tempFile);
        } catch (final Exception e) {  
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof ParserException) throw (ParserException) e;
            
            throw new ParserException("Unexpected error while parsing bzip file. " + e.getMessage(),location);
        } finally {
            if (tempFile != null) FileUtils.deletedelete(tempFile);
        }
    }
    
    @Override
    public void reset() {
        // Nothing todo here at the moment
        super.reset();
    }
}