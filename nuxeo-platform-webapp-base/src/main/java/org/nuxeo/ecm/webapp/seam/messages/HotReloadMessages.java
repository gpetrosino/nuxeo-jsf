/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.webapp.seam.messages;

import static org.jboss.seam.annotations.Install.APPLICATION;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.intercept.BypassInterceptors;
import org.jboss.seam.core.SeamResourceBundle;
import org.jboss.seam.international.Messages;

/**
 * Factory for a Map that contains interpolated messages defined in the Seam
 * ResourceBundle.
 * <p>
 * Overriden to add a control over reload of bundle for hot reload
 *
 * @see org.jboss.seam.core.SeamResourceBundle
 * @author Gavin King
 * @since 5.6
 */
@Scope(ScopeType.STATELESS)
@BypassInterceptors
@Name("org.jboss.seam.international.messagesFactory")
// XXX: since debug mode cannot be set by using nuxeo debug/dev mode, make
// sure this component is deployed even in production => debug = false
@Install(precedence = APPLICATION, debug = false)
public class HotReloadMessages extends Messages {

    @Override
    @SuppressWarnings("rawtypes")
    // mostly copy/pasted from parent class
    protected Map createMap() {

        // AbstractMap uses the implementation of entrySet to perform all its
        // operations - for a resource bundle this is very inefficient for keys
        return new AbstractMap<String, String>() {

            private ResourceBundle seamResourceBundle = getSeamResourceBundle();
            /**
             * Returns usual seam bundle, but with a custom resource control
             */
            protected ResourceBundle getSeamResourceBundle() {
                ResourceBundle.Control control = HotReloadResourceBundleControl.instance();
                return ResourceBundle.getBundle(SeamResourceBundle.class.getName(),
                        org.jboss.seam.core.Locale.instance(),
                        Thread.currentThread().getContextClassLoader(), control);
            }

            @Override
            public String get(Object key) {
                if (key instanceof String) {
                    String resourceKey = (String) key;
                    String resource = null;
                    ResourceBundle bundle = seamResourceBundle;
                    if (bundle != null) {
                        try {
                            resource = bundle.getString(resourceKey);
                        } catch (MissingResourceException mre) {
                            // Just swallow
                        }
                    }
                    return resource == null ? resourceKey : resource;
                } else {
                    return null;
                }
            }

            @Override
            public Set<Map.Entry<String, String>> entrySet() {
                ResourceBundle bundle = seamResourceBundle;
                Enumeration<String> keys = bundle.getKeys();
                Map<String, String> map = new HashMap<String, String>();
                while (keys.hasMoreElements()) {
                    String key = keys.nextElement();
                    map.put(key, get(key));
                }
                return Collections.unmodifiableSet(map.entrySet());
            }

            @Override
            public boolean containsKey(Object key) {
                return get(key) != null;
            }

            @Override
            public Set<String> keySet() {
                ResourceBundle bundle = seamResourceBundle;
                Enumeration<String> keys = bundle.getKeys();
                return new HashSet<String>(Collections.list(keys));
            }

            @Override
            public int size() {
                return keySet().size();
            }

        };
    }

}
