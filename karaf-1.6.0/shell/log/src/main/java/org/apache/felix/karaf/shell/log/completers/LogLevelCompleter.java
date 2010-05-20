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
package org.apache.felix.karaf.shell.log.completers;

import java.util.List;

import org.apache.felix.karaf.shell.console.Completer;
import org.apache.felix.karaf.shell.console.completer.StringsCompleter;
import org.apache.felix.karaf.shell.log.Level;

/**
 * {@link Completer} implementation for completing log levels  
 */
public class LogLevelCompleter extends StringsCompleter {
    
    public LogLevelCompleter() {
        super(Level.strings());
    }
    
    @Override @SuppressWarnings("unchecked")
    public int complete(String buffer, int cursor, List candidates) {
        if (buffer == null) {
            return super.complete(null, cursor, candidates);
        } else {
            // support completing lower case as well with the toUpperCase() call
            return super.complete(buffer.toUpperCase(), cursor, candidates);
        }
    }
}
