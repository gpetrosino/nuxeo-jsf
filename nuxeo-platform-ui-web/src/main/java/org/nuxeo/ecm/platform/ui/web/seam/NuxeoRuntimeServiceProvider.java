/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Nuxeo - initial API and implementation
 *
 */

package org.nuxeo.ecm.platform.ui.web.seam;

import static org.jboss.seam.annotations.Install.FRAMEWORK;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.Install;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.annotations.intercept.BypassInterceptors;
import org.jboss.seam.contexts.Contexts;
import org.jboss.seam.core.providers.ServiceProvider;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.api.Framework;

/**
 *
 * Provide simple extension to Seam injection system to be able to inject Nuxeo
 * Services and Nuxeo Components inside Seam Beans
 *
 * @since 5.7.3
 *
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 *
 */
@Scope(ScopeType.STATELESS)
@Name(ServiceProvider.NAME)
@Install(precedence = FRAMEWORK)
@BypassInterceptors
public class NuxeoRuntimeServiceProvider implements ServiceProvider {

    private static final Log log = LogFactory.getLog(NuxeoRuntimeServiceProvider.class);

    @SuppressWarnings("rawtypes")
    protected static Map<String, Class> name2ServiceClassCache = new HashMap<String, Class>();

    protected static ReentrantLock name2ServiceClassLock = new ReentrantLock();

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object lookup(String name, Class type, boolean create) {

        if (Framework.getRuntime() == null) {
            return null;
        }

        if (type != null && type.isAssignableFrom(CoreSession.class)) {
            // XXX return a CoreSession on the default repository ?
            return null;
        }

        Object result = null;

        // service loopkup
        if (type != null) {
            result = Framework.getLocalService(type);
        }

        // fallback on component lookup
        if (result == null && name != null) {
            if (!name.startsWith("org.jboss")) {
                result = Framework.getRuntime().getComponent(name);
                // lookup service by short name
                if (result == null) {
                    result = findServiceByShortCut(name);
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Nuxeo Lookup => return " + result);
        }
        return result;

    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Object findServiceByShortCut(String name) {

        if (!name2ServiceClassCache.containsKey(name)) {
            Class klass = null;
            name2ServiceClassLock.lock();
            try {
                for (String serviceClassName : Framework.getRuntime().getComponentManager().getServices()) {
                    int p = serviceClassName.lastIndexOf('.');
                    String fullClassName = serviceClassName;
                    if (p > -1) {
                        serviceClassName = serviceClassName.substring(p + 1);
                    }
                    if (name.equalsIgnoreCase(serviceClassName)) {
                        try {
                            klass = Thread.currentThread().getContextClassLoader().loadClass(
                                    fullClassName);
                            if (log.isDebugEnabled()) {
                                log.debug("Lookup for " + name
                                        + " resolved to service "
                                        + fullClassName);
                            }
                            break;
                        } catch (ClassNotFoundException e) {
                            log.error("Unable to load class for service "
                                    + fullClassName, e);
                        }
                    }
                }
                // NB : puts null if not found to avoid multiple lookups
                name2ServiceClassCache.put(name, klass);
            } finally {
                name2ServiceClassLock.unlock();
            }
        }
        Class serviceClass = name2ServiceClassCache.get(name);
        Object result = null;
        if (serviceClass != null) {
            result = Framework.getLocalService(serviceClass);
            if (result != null && Contexts.isEventContextActive()) {
                // cache in Event scope
                Contexts.getEventContext().set(name, result);
            }
        }
        return result;
    }

}
