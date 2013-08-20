package org.xwalk.runtime.extension;

import org.xwalk.runtime.XWalkCoreProvider;

import android.app.Activity;

public class ExtensionEntry {

    /**
     * The name of the service that this extension implements
     */
    public String service = "";

    /**
     * The extension class name that implements the service.
     */
    public String extensionClass = "";

    /**
     * The extension object.
     * extension objects are only created when they are called from JavaScript.  (see XWalkExtensionManager.exec)
     * The exception is if the onload flag is set, then they are created when XWalkExtensionManager is initialized.
     */
    public XWalkExtension extension = null;

    /**
     * Flag that indicates the extension object should be created when XWalkExtensionManager is initialized.
     */
    public boolean onload = false;

    /**
     * Constructor
     *
     * @param service               The name of the service
     * @param extensionClass        The extension class name
     * @param onload                Create extension object when HTML page is loaded
     */
    public ExtensionEntry(String service, String extensionClass, boolean onload) {
        this.service = service;
        this.extensionClass = extensionClass;
        this.onload = onload;
    }

	/**
     * Create extension object.
     * If extension is already created, then just return it.
     *
     * @return                      The extension object
     */
    public XWalkExtension createExtension(XWalkCoreProvider app, Activity activity) {
        if (this.extension != null) {
            return this.extension;
        }
        try {
            @SuppressWarnings("rawtypes")
            Class c = getClassByName(this.extensionClass);
            if (isXwalkExtension(c)) {
                this.extension = (XWalkExtension) c.newInstance();
                this.extension.initialize(activity, app);
                return extension;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error adding extension " + this.extensionClass + ".");
        }
        return null;
    }

    /**
     * Get the class.
     *
     * @param clazz
     * @return
     * @throws ClassNotFoundException
     */
    @SuppressWarnings("rawtypes")
    private Class getClassByName(final String clazz) throws ClassNotFoundException {
        Class c = null;
        if (clazz != null) {
            c = Class.forName(clazz);
        }
        return c;
    }

    /**
     * Returns whether the given class extends XWalkExtension.
     */
    @SuppressWarnings("rawtypes")
    private boolean isXwalkExtension(Class c) {
        if (c != null) {
            return XWalkExtension.class.isAssignableFrom(c);
        }
        return false;
    }
}
