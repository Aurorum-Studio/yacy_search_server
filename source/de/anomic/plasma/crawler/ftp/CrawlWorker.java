// CrawlerWorker.java 
// -------------------------------------
// part of YACY
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
//
// This file ist contributed by Martin Thelian
//
// $LastChangedDate: 2006-02-20 23:57:42 +0100 (Mo, 20 Feb 2006) $
// $LastChangedRevision: 1715 $
// $LastChangedBy: theli $
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
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.


package de.anomic.plasma.crawler.ftp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import de.anomic.net.ftpc;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.crawler.AbstractCrawlWorker;
import de.anomic.plasma.crawler.plasmaCrawlWorker;
import de.anomic.plasma.crawler.plasmaCrawlerPool;
import de.anomic.plasma.plasmaHTCache.Entry;
import de.anomic.server.logging.serverLog;

public class CrawlWorker extends AbstractCrawlWorker implements
        plasmaCrawlWorker {

    public CrawlWorker(ThreadGroup theTG, plasmaCrawlerPool thePool, plasmaSwitchboard theSb, plasmaHTCache theCacheManager, serverLog theLog) {
        super(theTG, thePool, theSb, theCacheManager, theLog);
        
        // this crawler supports ftp
        this.protocol = "ftp";  
    }

    public void close() {
        // TODO Auto-generated method stub

    }

    public void init() {
        // TODO Auto-generated method stub
    }

    public Entry load() throws IOException {
                       
        File cacheFile = cacheManager.getCachePath(url);
        cacheFile.getParentFile().mkdirs();
        
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bout);
        
        ByteArrayOutputStream berr = new ByteArrayOutputStream();
        PrintStream err = new PrintStream(berr);            
        
        ftpc ftpClient = new ftpc(System.in, out, err);
        
        String userInfo = this.url.getUserInfo();
        String userName, userPwd;
        if (userInfo != null) {
            int pos = userInfo.indexOf(":");
            userName = userInfo.substring(0,pos);
            userPwd = userInfo.substring(pos+1);
        } else {
            userName = "anonymous";
            userPwd = "anonymous";
        }
        
        ftpClient.exec("open " + this.url.getHost(), false);
        ftpClient.exec("user " + userName + " " + userPwd, false);        
        ftpClient.exec("binary", false);
        
        // cd
        String file = "";
        String path = this.url.getPath();        
        int pos = path.lastIndexOf("/");
        if (pos == -1) {
            file = path;
            path = "/";
        } else {
            file = path.substring(pos+1);
            path = path.substring(0,pos);            
        }
        ftpClient.exec("cd \"" + path + "\"", false);
        
        if (ftpClient.isFolder(file)) {
            ftpClient.exec("cd \"" + file + "\"", false);
            
            // TODO: dirlist
        } else {
            // download the remote file
            ftpClient.exec("get \"" + file + "\" \"" + cacheFile.getAbsolutePath() + "\"", false);            
        }
        
        ftpClient.exec("close", false);
        ftpClient.exec("exit", false);        
        
        // TODO: create a new htCache entry ....
        
        return null;
    }

}
