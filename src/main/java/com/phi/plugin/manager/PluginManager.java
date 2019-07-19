package com.phi.plugin.manager;

import com.phi.plugin.annotation.Plugin;
import com.phi.plugin.exception.PluginException;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class PluginManager {

    private static Map<String, List<Class<?>>> jarNameToPluginMap;

    /**
     * Call this method to initialize PluginManager
     */
    public static void init(File pluginDirectory) {
        setJarNameToPluginMap(pluginDirectory);
    }

    /**
     * Get new plugin instance by the given parameters
     */
    public static <T> T getNewPlugin(String jarName, String pluginName, String uuid, Class<T> clazz) {
        if (jarNameToPluginMap == null) {
            throw new PluginException("The PluginManager not initialize");
        }

        List<Class<?>> classList = jarNameToPluginMap.get(jarName);
        if (classList == null) {
            throw new PluginException("Not found jar file=" + jarName);
        }

        for (Class<?> c : classList) {
            Plugin plugin = c.getAnnotation(Plugin.class);

            if (plugin.name().equals(pluginName)) {
                if (!plugin.uuid().equals(uuid)) {
                    throw new PluginException("UUID not match for plugin=" + pluginName);
                }

                try {
                    return clazz.cast(c.getConstructor().newInstance());
                } catch (Exception e) {
                    throw new PluginException("Fail to new instance for class=" + c.getName(), e);
                }
            }
        }

        throw new PluginException("Not found plugin=" + pluginName);
    }

    private static void setJarNameToPluginMap(File pluginDirectory) {
        if (pluginDirectory == null || !pluginDirectory.isDirectory()) {
            throw new PluginException("The plugin directory not found");
        }

        File[] jars = pluginDirectory.listFiles((File dir, String name) -> name.endsWith(".jar"));
        jarNameToPluginMap = Arrays.stream(jars)
                .collect(Collectors.toMap(File::getName, jar -> getAllPlugin(jar)));
    }

    private static List<Class<?>> getAllPlugin(File jar) {
        List<String> classNames = getAllClassName(jar);
        ClassLoader classLoader = getClassLoader(jar);

        Set<String> checkNameSet = new HashSet<>();
        List<Class<?>> classList = new ArrayList<>();

        for (String className : classNames) {
            try {
                Class<?> clazz = classLoader.loadClass(className);
                Plugin plugin = clazz.getAnnotation(Plugin.class);

                if (plugin != null) {
                    if (!plugin.name().isEmpty() && !checkNameSet.add(plugin.name())) {
                        throw new PluginException("Same plugin name=" + plugin.name() + " in " + jar.getName());
                    }
                    classList.add(clazz);
                }

            } catch (ClassNotFoundException e) {
                throw new PluginException("Fail to load class=" + className, e);
            }
        }

        return classList;
    }

    private static ClassLoader getClassLoader(File jar) {
        try {
            URL url = jar.toURI().toURL();
            return URLClassLoader.newInstance(new URL[]{url}, PluginManager.class.getClassLoader());
        } catch (MalformedURLException e) {
            throw new PluginException("Fail to load jar=" + jar, e);
        }
    }

    private static List<String> getAllClassName(File jar) {
        List<String> classNames = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(jar); ZipInputStream zip = new ZipInputStream(fis)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace('/', '.');
                    classNames.add(className.substring(0, className.length() - 6));
                }
            }
        } catch (IOException e) {
            throw new PluginException("Fail to load jar=" + jar, e);
        }
        return classNames;
    }

}
