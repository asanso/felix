/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.webconsole.internal.misc;


import java.io.*;
import java.net.URL;
import java.text.*;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.felix.webconsole.*;
import org.apache.felix.webconsole.internal.OsgiManagerPlugin;
import org.apache.felix.webconsole.internal.i18n.ResourceBundleManager;
import org.osgi.framework.Bundle;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;


/**
 * ConfigurationRender plugin renders the configuration status - a textual
 * representation of the current framework status. The content itself is
 *  internally generated by the {@link ConfigurationPrinter} plugins.
 */
public class ConfigurationRender extends SimpleWebConsolePlugin implements OsgiManagerPlugin
{

    private static final String LABEL = "config";
    private static final String TITLE = "%configStatus.pluginTitle";
    private static final String[] CSS_REFS = { "/res/ui/configurationrender.css" };

    // use English as the locale for all non-display titles
    private static final Locale DEFAULT = Locale.ENGLISH;

    /**
     * Formatter pattern to generate a relative path for the generation
     * of the plain text or zip file representation of the status. The file
     * name consists of a base name and the current time of status generation.
     */
    private static final SimpleDateFormat FILE_NAME_FORMAT = new SimpleDateFormat( "'" + LABEL
        + "/configuration-status-'yyyyMMdd'-'HHmmZ" );

    /**
     * Formatter pattern to render the current time of status generation.
     */
    private static final DateFormat DISPLAY_DATE_FORMAT = DateFormat.getDateTimeInstance( DateFormat.LONG,
        DateFormat.LONG, Locale.US );

    /**
     * The resource bundle manager to allow for status printer title
     * localization
     */
    private final ResourceBundleManager resourceBundleManager;

    private ServiceTracker cfgPrinterTracker;

    private int cfgPrinterTrackerCount;

    private ArrayList configurationPrinters;

    /** Default constructor */
    public ConfigurationRender( final ResourceBundleManager resourceBundleManager )
    {
        super( LABEL, TITLE, CSS_REFS );
        this.resourceBundleManager = resourceBundleManager;
    }


    /**
     * @see org.apache.felix.webconsole.SimpleWebConsolePlugin#deactivate()
     */
    public void deactivate()
    {
        // make sure the service tracker is closed and removed on deactivate
        ServiceTracker oldTracker = cfgPrinterTracker;
        if ( oldTracker != null )
        {
            oldTracker.close();
        }
        cfgPrinterTracker = null;
        configurationPrinters = null;

        super.deactivate();
    }


    /**
     * @see org.apache.felix.webconsole.AbstractWebConsolePlugin#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    protected final void doGet( HttpServletRequest request, HttpServletResponse response ) throws ServletException,
        IOException
    {
        if ( request.getPathInfo().endsWith( ".txt" ) )
        {
            response.setContentType( "text/plain; charset=utf-8" );
            ConfigurationWriter pw = new PlainTextConfigurationWriter( response.getWriter() );
            printConfigurationStatus( pw, ConfigurationPrinter.MODE_TXT );
            pw.flush();
        }
        else if ( request.getPathInfo().endsWith( ".zip" ) )
        {
            String type = getServletContext().getMimeType( request.getPathInfo() );
            if ( type == null )
            {
                type = "application/x-zip";
            }
            response.setContentType( type );

            ZipOutputStream zip = new ZipOutputStream( response.getOutputStream() );
            zip.setLevel( Deflater.BEST_SPEED );
            zip.setMethod( ZipOutputStream.DEFLATED );

            final ConfigurationWriter pw = new ZipConfigurationWriter( zip );
            printConfigurationStatus( pw, ConfigurationPrinter.MODE_ZIP );
            pw.flush();

            addAttachments( pw, ConfigurationPrinter.MODE_ZIP );
            zip.finish();
        }
        else if ( request.getPathInfo().endsWith( ".nfo" ) )
        {
            WebConsoleUtil.setNoCache( response );
            response.setContentType( "text/html; charset=utf-8" );

            String name = request.getPathInfo();
            name = name.substring( name.lastIndexOf('/') + 1);
            name = name.substring(0, name.length() - 4);
            name = WebConsoleUtil.urlDecode( name );

            ConfigurationWriter pw = new HtmlConfigurationWriter( response.getWriter() );
            pw.println ( "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\"" );
            pw.println ( "  \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">" );
            pw.println ( "<html xmlns=\"http://www.w3.org/1999/xhtml\">" );
            pw.println ( "<head><title>dummy</title></head><body><div>" );

            Collection printers = getConfigurationPrinters();
            for (Iterator i = printers.iterator(); i.hasNext();)
            {
                final PrinterDesc desc = (PrinterDesc) i.next();
                if (desc.label.equals( name ) )
                {
                    printConfigurationPrinter( pw, desc, ConfigurationPrinter.MODE_WEB );
                    pw.println( "</div></body></html>" );
                    return;
                }
            }

            response.sendError( HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid configuration printer: " + name);
        }
        else
        {
            super.doGet( request, response );
        }
    }


    /**
     * @see org.apache.felix.webconsole.AbstractWebConsolePlugin#renderContent(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    protected final void renderContent( HttpServletRequest request, HttpServletResponse response ) throws IOException
    {

        //ConfigurationWriter pw = new HtmlConfigurationWriter( response.getWriter() );
        PrintWriter pw = response.getWriter();
        pw.println( "<script type='text/javascript' src='${appRoot}/res/ui/ui.tabs.paging.js'></script>" );
        pw.println( "<script type='text/javascript'>" );
        pw.println( "// <![CDATA[" );
        pw.println( "$(document).ready(function() {$('#tabs').tabs().tabs('paging')} );" );
        pw.println( "// ]]>" );
        pw.println( "</script>" );

        pw.println("<br/><p class=\"statline\">");

        final Date currentTime = new Date();
        synchronized ( DISPLAY_DATE_FORMAT )
        {
            pw.print("Date: ");
            pw.println(DISPLAY_DATE_FORMAT.format(currentTime));
        }

        synchronized ( FILE_NAME_FORMAT )
        {
            String fileName = FILE_NAME_FORMAT.format( currentTime );
            pw.print("<br/>Download as <a href='");
            pw.print(fileName);
            pw.print(".txt'>[Single File]</a> or as <a href='");
            pw.print(fileName);
            pw.println(".zip'>[ZIP]</a>");
        }

        pw.println("</p>"); // status line

        // display some information while the data is loading
        // load the data (hidden to begin with)
        pw.println("<div id='tabs'> <!-- tabs container -->");
        pw.println("<ul> <!-- tabs on top -->");

        // print headers only
        final String pluginRoot = request.getAttribute( WebConsoleConstants.ATTR_PLUGIN_ROOT ) + "/";
        Collection printers = getConfigurationPrinters();
        for (Iterator i = printers.iterator(); i.hasNext();)
        {
            final PrinterDesc desc = (PrinterDesc) i.next();
            final String label = desc.label;
            final String title = desc.title;
            pw.print("<li><a href='" + pluginRoot + label + ".nfo'>" + title + "</a></li>" );
        }
        pw.println("</ul> <!-- end tabs on top -->");
        pw.println();

        pw.println("</div> <!-- end tabs container -->");

        pw.flush();
    }


    private void printConfigurationStatus( ConfigurationWriter pw, final String mode )
    {
        for ( Iterator cpi = getConfigurationPrinters().iterator(); cpi.hasNext(); )
        {
            final PrinterDesc desc = (PrinterDesc) cpi.next();
            if ( desc.match(mode) )
            {
                printConfigurationPrinter( pw, desc, mode );
            }
        }
    }


    private final ArrayList getConfigurationPrinters()
    {
        if ( cfgPrinterTracker == null )
        {
            cfgPrinterTracker = new ServiceTracker( getBundleContext(), ConfigurationPrinter.SERVICE, null );
            cfgPrinterTracker.open();
            cfgPrinterTrackerCount = -1;
        }

        if ( cfgPrinterTrackerCount != cfgPrinterTracker.getTrackingCount() )
        {
            SortedMap cp = new TreeMap();
            ServiceReference[] refs = cfgPrinterTracker.getServiceReferences();
            if ( refs != null )
            {
                for ( int i = 0; i < refs.length; i++ )
                {
                    ConfigurationPrinter cfgPrinter = ( ConfigurationPrinter ) cfgPrinterTracker.getService( refs[i] );
                    addConfigurationPrinter( cp, cfgPrinter, refs[i].getBundle(), refs[i]
                        .getProperty( WebConsoleConstants.PLUGIN_LABEL ), refs[i]
                        .getProperty( ConfigurationPrinter.PROPERTY_MODES ) );
                }
            }
            configurationPrinters = new ArrayList(cp.values());
            cfgPrinterTrackerCount = cfgPrinterTracker.getTrackingCount();
        }

        return configurationPrinters;
    }


    private final void addConfigurationPrinter( final SortedMap printers, final ConfigurationPrinter cfgPrinter,
        final Bundle provider, final Object labelProperty, final Object mode )
    {
        if ( cfgPrinter != null )
        {
            final String title = getTitle( cfgPrinter.getTitle(), provider );
            String sortKey = title;
            if ( printers.containsKey( sortKey ) )
            {
                int idx = -1;
                String idxTitle;
                do
                {
                    idx++;
                    idxTitle = sortKey + idx;
                }
                while ( printers.containsKey( idxTitle ) );
                sortKey = idxTitle;
            }
            String label = ( labelProperty instanceof String ) ? ( String ) labelProperty : sortKey;
            printers.put( sortKey, new PrinterDesc( cfgPrinter, title, label, mode ) );
        }
    }


    // This is Sling stuff, we comment it out for now
    //    private void printRawFrameworkProperties(PrintWriter pw) {
    //        pw.println("*** Raw Framework properties:");
    //
    //        File file = new File(getBundleContext().getProperty("sling.home"),
    //            "sling.properties");
    //        if (file.exists()) {
    //            Properties props = new Properties();
    //            InputStream ins = null;
    //            try {
    //                ins = new FileInputStream(file);
    //                props.load(ins);
    //            } catch (IOException ioe) {
    //                // handle or ignore
    //            } finally {
    //                IOUtils.closeQuietly(ins);
    //            }
    //
    //            SortedSet keys = new TreeSet(props.keySet());
    //            for (Iterator ki = keys.iterator(); ki.hasNext();) {
    //                Object key = ki.next();
    //                infoLine(pw, null, (String) key, props.get(key));
    //            }
    //
    //        } else {
    //            pw.println("  No Framework properties in " + file);
    //        }
    //
    //        pw.println();
    //    }


    private final void printConfigurationPrinter( final ConfigurationWriter pw, final PrinterDesc desc,
        final String mode )
    {
        pw.title( desc.title );
        final ConfigurationPrinter cp = desc.printer;
        if ( cp instanceof ModeAwareConfigurationPrinter )
        {
            ( ( ModeAwareConfigurationPrinter ) cp ).printConfiguration( pw, mode );
        }
        else
        {
            cp.printConfiguration( pw );
        }
        pw.end();
    }


    /**
     * Renders an info line - element in the framework configuration. The info line will
     * look like:
     * <pre>
     * label = value
     * </pre>
     *
     * Optionally it can be indented by a specific string.
     *
     * @param pw the writer to print to
     * @param indent indentation string
     * @param label the label data
     * @param value the data itself.
     */
    public static final void infoLine( PrintWriter pw, String indent, String label, Object value )
    {
        if ( indent != null )
        {
            pw.print( indent );
        }

        if ( label != null )
        {
            pw.print( label );
            pw.print( " = " );
        }

        pw.print( asString( value ) );

        pw.println();
    }


    private static final String asString( final Object value )
    {
        if ( value == null )
        {
            return "n/a";
        }
        else if ( value.getClass().isArray() )
        {
            StringBuffer dest = new StringBuffer();
            Object[] values = ( Object[] ) value;
            for ( int j = 0; j < values.length; j++ )
            {
                if ( j > 0 )
                    dest.append( ", " );
                dest.append( values[j] );
            }
            return dest.toString();
        }
        else
        {
            return value.toString();
        }
    }


    private final String getTitle( final String title, final Bundle provider )
    {
        if ( !title.startsWith( "%" ) )
        {
            return title;
        }

        ResourceBundle res = resourceBundleManager.getResourceBundle( provider, DEFAULT );
        return res.getString( title.substring( 1 ) );
    }

    private abstract static class ConfigurationWriter extends PrintWriter
    {

        ConfigurationWriter( Writer delegatee )
        {
            super( delegatee );
        }


        abstract void title( String title );


        abstract void end();


        public void handleAttachments( final String title, final URL[] urls ) throws IOException
        {
            throw new UnsupportedOperationException( "handleAttachments not supported by this configuration writer: "
                + this );
        }

    }

    private static class HtmlConfigurationWriter extends ConfigurationWriter
    {

        // whether or not to filter "<" signs in the output
        private boolean doFilter;


        HtmlConfigurationWriter( Writer delegatee )
        {
            super( delegatee );
        }


        public void title( String title )
        {
            doFilter = true;
        }


        public void end()
        {
            doFilter = false;
        }


        // IE has an issue with white-space:pre in our case so, we write
        // <br/> instead of [CR]LF to get the line break. This also works
        // in other browsers.
        public void println()
        {
            if ( doFilter )
            {
                super.write( "<br/>", 0, 5 );
            }
            else
            {
                super.println();
            }
        }


        // write the character unmodified unless filtering is enabled and
        // the character is a "<" in which case &lt; is written
        public void write( final int character )
        {
            if ( doFilter && character == '<' )
            {
                super.write( "&lt;" );
            }
            else
            {
                super.write( character );
            }
        }


        // write the characters unmodified unless filtering is enabled in
        // which case the writeFiltered(String) method is called for filtering
        public void write( final char[] chars, final int off, final int len )
        {
            if ( doFilter )
            {
                writeFiltered( new String( chars, off, len ) );
            }
            else
            {
                super.write( chars, off, len );
            }
        }


        // write the string unmodified unless filtering is enabled in
        // which case the writeFiltered(String) method is called for filtering
        public void write( final String string, final int off, final int len )
        {
            if ( doFilter )
            {
                writeFiltered( string.substring( off, len ) );
            }
            else
            {
                super.write( string, off, len );
            }
        }


        // helper method filter the string for "<" before writing
        private void writeFiltered( String string )
        {
            string = WebConsoleUtil.escapeHtml(string); // filtering
            super.write( string, 0, string.length() );
        }
    }

    private void addAttachments( final ConfigurationWriter cf, final String mode )
    throws IOException
    {
        for ( Iterator cpi = getConfigurationPrinters().iterator(); cpi.hasNext(); )
        {
            // check if printer supports zip mode
            final PrinterDesc desc = (PrinterDesc) cpi.next();
            if ( desc.match(mode) )
            {
                // check if printer implements binary configuration printer
                if ( desc.printer instanceof AttachmentProvider )
                {
                    final URL[] attachments = ((AttachmentProvider)desc.printer).getAttachments(mode);
                    if ( attachments != null )
                    {
                        cf.handleAttachments( desc.title, attachments );
                    }
                }
            }
        }

    }

    private static final class PrinterDesc
    {
        public final ConfigurationPrinter printer;
        public final String title;
        public final String label;
        private final String[] modes;

        private static final List CUSTOM_MODES = new ArrayList();
        static
        {
            CUSTOM_MODES.add( ConfigurationPrinter.MODE_TXT );
            CUSTOM_MODES.add( ConfigurationPrinter.MODE_WEB );
            CUSTOM_MODES.add( ConfigurationPrinter.MODE_ZIP );
        }


        public PrinterDesc( final ConfigurationPrinter printer, final String title, final String label,
            final Object modes )
        {
            this.printer = printer;
            this.title = title;
            this.label = label;
            if ( modes == null || !( modes instanceof String || modes instanceof String[] ) )
            {
                this.modes = null;
            }
            else
            {
                if ( modes instanceof String )
                {
                    if ( CUSTOM_MODES.contains(modes) )
                    {
                        this.modes = new String[] {modes.toString()};
                    }
                    else
                    {
                        this.modes = null;
                    }
                }
                else
                {
                    final String[] values = (String[])modes;
                    boolean valid = values.length > 0;
                    for(int i=0; i<values.length; i++)
                    {
                        if ( !CUSTOM_MODES.contains(values[i]) )
                        {
                            valid = false;
                            break;
                        }
                    }
                    if ( valid)
                    {
                        this.modes = values;
                    }
                    else
                    {
                        this.modes = null;
                    }
                }
            }
        }

        public boolean match(final String mode)
        {
            if ( this.modes == null)
            {
                return true;
            }
            for(int i=0; i<this.modes.length; i++)
            {
                if ( this.modes[i].equals(mode) )
                {
                    return true;
                }
            }
            return false;
        }
    }

    private static class PlainTextConfigurationWriter extends ConfigurationWriter
    {

        PlainTextConfigurationWriter( Writer delegatee )
        {
            super( delegatee );
        }


        public void title( String title )
        {
            print( "*** " );
            print( title );
            println( ":" );
        }


        public void end()
        {
            println();
        }
    }

    private static class ZipConfigurationWriter extends ConfigurationWriter
    {
        private final ZipOutputStream zip;

        private int counter;


        ZipConfigurationWriter( ZipOutputStream zip )
        {
            super( new OutputStreamWriter( zip ) );
            this.zip = zip;
        }


        public void title( String title )
        {
            String name = MessageFormat.format( "{0,number,000}-{1}.txt", new Object[]
                { new Integer( counter ), title } );

            counter++;

            ZipEntry entry = new ZipEntry( name );
            try
            {
                zip.putNextEntry( entry );
            }
            catch ( IOException ioe )
            {
                // should handle
            }
        }

        private OutputStream startFile( String title, String name)
        {
            final String path = MessageFormat.format( "{0,number,000}-{1}/{2}", new Object[]
                 { new Integer( counter ), title, name } );
            ZipEntry entry = new ZipEntry( path );
            try
            {
                zip.putNextEntry( entry );
            }
            catch ( IOException ioe )
            {
                // should handle
            }
            return zip;
        }

        public void handleAttachments( final String title, final URL[] attachments)
        throws IOException
        {
            for(int i = 0; i < attachments.length; i++)
            {
                final URL current = attachments[i];
                final String path = current.getPath();
                final String name;
                if ( path == null || path.length() == 0 )
                {
                    // sanity code, we should have a path, but if not let's
                    // just create some random name
                    name = "file" + Double.doubleToLongBits( Math.random() );
                }
                else
                {
                    final int pos = path.lastIndexOf('/');
                    name = (pos == -1 ? path : path.substring(pos + 1));
                }
                final OutputStream os = this.startFile(title, name);
                final InputStream is = current.openStream();
                try
                {
                    IOUtils.copy(is, os);
                }
                finally
                {
                    IOUtils.closeQuietly(is);
                }
                this.end();
            }

            // increase the filename counter
            counter++;
        }


        public void end()
        {
            flush();

            try
            {
                zip.closeEntry();
            }
            catch ( IOException ioe )
            {
                // should handle
            }
        }
    }
}
