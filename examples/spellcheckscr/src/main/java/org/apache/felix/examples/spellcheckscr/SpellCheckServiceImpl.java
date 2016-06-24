/*
 *   Copyright 2006-2016 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.apache.felix.examples.spellcheckscr;



import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;



/**
 * This class re-implements the spell check service of Example 6. This service
 * implementation behaves exactly like the one in Example 6, specifically, it
 * aggregates all available dictionary services, monitors their dynamic
 * availability, and only offers the spell check service if there are dictionary
 * services available. The service implementation is greatly simplified, though,
 * by using the Service Component Runtime. Notice that there is no OSGi references in the
 * application code; instead, the annotations describe the service
 * dependencies to the Service Component Runtime, which automatically manages them and it
 * also automatically registers the spell check services as appropriate.
 *
 * @author <a href="mailto:dev@felix.apache.org">Felix Project Team</a>
 */
@Component
public class SpellCheckServiceImpl
{
    
    @Activate
    private void activate(final BundleContext context ) throws BundleException {
        context.getBundle().uninstall();
    }
}
