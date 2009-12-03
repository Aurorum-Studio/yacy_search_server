// OAIPMHImporter
// (C) 2009 by Michael Peter Christen; mc@yacy.net, Frankfurt a. M., Germany
// first published 30.09.2009 on http://yacy.net
//
// This is a part of YaCy, a peer-to-peer based web search engine
//
// $LastChangedDate: 2009-09-23 23:26:14 +0200 (Mi, 23 Sep 2009) $
// $LastChangedRevision: 6340 $
// $LastChangedBy: low012 $
//
// LICENSE
// 
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.document.importer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.yacy.kelondro.data.meta.DigestURI;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.DateFormatter;
import net.yacy.repository.LoaderDispatcher;
import net.yacy.document.parser.csvParser;

import de.anomic.crawler.CrawlProfile;
import de.anomic.search.Switchboard;


// get one server with
// http://roar.eprints.org/index.php?action=csv
// or
// http://www.openarchives.org/Register/BrowseSites
// or
// http://www.openarchives.org/Register/ListFriends
//
// list records from oai-pmh like
// http://opus.bsz-bw.de/fhhv/oai2/oai2.php?verb=ListRecords&metadataPrefix=oai_dc


public class OAIPMHImporter extends Thread implements Importer, Comparable<OAIPMHImporter> {

    private static int importerCounter = Integer.MAX_VALUE;
    
    public static TreeSet<OAIPMHImporter> startedJobs = new TreeSet<OAIPMHImporter>();
    public static TreeSet<OAIPMHImporter> runningJobs = new TreeSet<OAIPMHImporter>();
    public static TreeSet<OAIPMHImporter> finishedJobs = new TreeSet<OAIPMHImporter>();
    
    private LoaderDispatcher loader;
    private DigestURI source;
    private int recordsCount, chunkCount;
    private long startTime, finishTime;
    private ResumptionToken resumptionToken;
    private String message;
    private int serialNumber;
    
    public OAIPMHImporter(LoaderDispatcher loader, DigestURI source) {
        this.serialNumber = importerCounter--;
        this.loader = loader;
        this.recordsCount = 0;
        this.chunkCount = 0;
        this.startTime = System.currentTimeMillis();
        this.finishTime = 0;
        this.resumptionToken = null;
        this.message = "import initialized";
        // fix start url
        String url = ResumptionToken.truncatedURL(source);
        if (!url.endsWith("?")) url = url + "?";
        try {
            this.source = new DigestURI(url + "verb=ListRecords&metadataPrefix=oai_dc", null);
        } catch (MalformedURLException e) {
            // this should never happen
            Log.logException(e);
        }
        startedJobs.add(this);
    }

    public int count() {
        return this.recordsCount;
    }
    
    public int chunkCount() {
        return this.chunkCount;
    }
    
    public String status() {
        return this.message;
    }
    
    public ResumptionToken getResumptionToken() {
        return this.resumptionToken;
    }

    public long remainingTime() {
        return (this.isAlive()) ? Long.MAX_VALUE : 0; // we don't know
    }

    public long runningTime() {
        return (this.isAlive()) ? System.currentTimeMillis() - this.startTime : this.finishTime - this.startTime;
    }

    public String source() {
        return source.toNormalform(true, false);
    }

    public int speed() {
        return (int) (1000L * ((long) count()) / runningTime());
    }
    
    public void run() {
        while (runningJobs.size() > 10) {
            try {Thread.sleep(1000 + 1000 * System.currentTimeMillis() % 6);} catch (InterruptedException e) {}
        }
        startedJobs.remove(this);
        runningJobs.add(this);
        this.message = "loading first part of records";
        while (true) {
            try {
                OAIPMHReader reader = new OAIPMHReader(this.loader, this.source, Switchboard.getSwitchboard().surrogatesInPath, filenamePrefix);
                this.chunkCount++;
                this.recordsCount += reader.getResumptionToken().getRecordCounter();
                this.source = reader.getResumptionToken().resumptionURL(this.source);
                if (this.source == null) {
                    this.message = "import terminated with source = null";
                    break;
                }
                this.message = "loading next resumption fragment, cursor = " + reader.getResumptionToken().getCursor();
            } catch (IOException e) {
                this.message = e.getMessage();
                break;
            }
        }
        this.finishTime = System.currentTimeMillis();
        runningJobs.remove(this);
        finishedJobs.add(this);
    }
    
    
    // methods that are needed to put the object into a Hashtable or a Map:
    
    public int hashCode() {
        return this.serialNumber;
    }
    
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof OAIPMHImporter)) return false;
        OAIPMHImporter other = (OAIPMHImporter) obj;
        return this.compareTo(other) == 0;
    }

    // methods that are needed to put the object into a Tree:
    public int compareTo(OAIPMHImporter o) {
        if (this.serialNumber > o.serialNumber) return 1;
        if (this.serialNumber < o.serialNumber) return -1;
        return 0;
    }
    
    public static Set<String> getUnloadedOAIServer(
            LoaderDispatcher loader,
            File surrogatesIn,
            File surrogatesOut,
            long staleLimit) {
        Set<String> plainList = getAllListedOAIServer(loader);
        Map<String, Date> loaded = getLoadedOAIServer(surrogatesIn, surrogatesOut);
        long limit = System.currentTimeMillis() - staleLimit;
        for (Map.Entry<String, Date> a: loaded.entrySet()) {
            if (a.getValue().getTime() > limit) plainList.remove(a.getKey());
        }
        return plainList;
    }
    
    /**
     * use the list server at http://roar.eprints.org/index.php?action=csv
     * to produce a list of OAI-PMH sources
     * @param loader
     * @return the list of oai-pmh sources
     */
    public static Set<String> getAllListedOAIServer(LoaderDispatcher loader) {
        TreeSet<String> list = new TreeSet<String>();

        // read roar
        File roar = new File(Switchboard.getSwitchboard().dictionariesPath, "harvesting/roar.csv");
        DigestURI roarSource;
        try {
            roarSource = new DigestURI("http://roar.eprints.org/index.php?action=csv", null);
        } catch (MalformedURLException e) {
            Log.logException(e);
            roarSource = null;
        }
        if (!roar.exists()) try {
            // load the file from the net
            loader.load(roarSource, CrawlProfile.CACHE_STRATEGY_NOCACHE, roar);
        } catch (IOException e) {
            Log.logException(e);
        }
        if (roar.exists()) {
            csvParser parser = new csvParser();
            try {
                List<String[]> table = parser.getTable(roarSource, "", "UTF-8", new FileInputStream(roar));
                for (String[] row: table) {
                    if (row.length > 2 && (row[2].startsWith("http://") || row[2].startsWith("https://"))) {
                        list.add(row[2]);
                    }
                }
            } catch (FileNotFoundException e) {
                Log.logException(e);
            }
        }
        
        return list;
    }

    /**
     * get a map for already loaded oai-pmh servers and their latest access date
     * @param surrogatesIn
     * @param surrogatesOut
     * @return a map where the key is the hostID of the servers and the value is the last access date
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Date> getLoadedOAIServer(File surrogatesIn, File surrogatesOut) {
        Map<String, Date> map = getLoadedOAIServer(surrogatesOut);
        map.putAll((Map<? extends String, ? extends Date>) getLoadedOAIServer(surrogatesIn).entrySet());
        return map;
    }
    
    private static Map<String, Date> getLoadedOAIServer(File surrogates) {
        HashMap<String, Date> map = new HashMap<String, Date>();
        //oaipmh_opus.bsz-bw.de_20091102113118728.xml
        for (String s: surrogates.list()) {
            if (s.startsWith(filenamePrefix) && s.endsWith(".xml") && s.charAt(s.length() - 22) == filenameSeparationChar) {
                try {
                    Date fd = DateFormatter.parseShortMilliSecond(s.substring(s.length() - 21, s.length() - 4));
                    String hostID = s.substring(7, s.length() - 22);
                    Date md = map.get(hostID);
                    if (md == null || fd.after(md)) map.put(hostID, fd);
                } catch (ParseException e) {
                    Log.logException(e);
                }
            }
        }
        return map;
    }

    public static final char hostReplacementChar = '_';
    public static final char filenameSeparationChar = '.';
    public static final String filenamePrefix = "oaipmh";

    /**
     * compute a host id that is also used in the getLoadedOAIServer method for the map key
     * @param source
     * @return a string that is a key for the given host
     */
    public static final String hostID(DigestURI source) {
        String s = ResumptionToken.truncatedURL(source);
        if (s.endsWith("?")) s = s.substring(0, s.length() - 1);
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.startsWith("https://")) s = s.substring(8);
        if (s.startsWith("http://")) s = s.substring(7);
        return s.replace('.', hostReplacementChar).replace('/', hostReplacementChar).replace(':', hostReplacementChar);
    }
    
    /**
     * get a file name for a source. the file name contains a prefix that is used to identify
     * that source as part of the OAI-PMH import process and a host key to identify the source.
     * also included is a date stamp within the file name
     * @param source
     * @return a file name for the given source. It will be different for each call for same hosts because it contains a date stamp
     */
    public static final String filename4Source(DigestURI source) {
        return filenamePrefix + OAIPMHImporter.filenameSeparationChar +
               OAIPMHImporter.hostID(source) + OAIPMHImporter.filenameSeparationChar +
               DateFormatter.formatShortMilliSecond(new Date()) + ".xml";
    }
}