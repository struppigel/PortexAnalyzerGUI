package io.github.struppigel.testsupport;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Helpers to create and inspect Swing components in tests without showing a window.
 */
public final class Swing {

    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private Swing() {}

    /**
     * Runs the code on the event dispatch thread and waits for it. Exceptions are rethrown in the calling thread, so
     * an exception in a listener fails the test.
     */
    public static void onEdt(ThrowingRunnable runnable) throws Exception {
        onEdtGet(() -> {
            runnable.run();
            return null;
        });
    }

    public static <T> T onEdtGet(Callable<T> callable) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(callable.call());
            } catch (Throwable t) {
                error.set(t);
            }
        });
        Throwable t = error.get();
        if (t instanceof Exception) throw (Exception) t;
        if (t instanceof Error) throw (Error) t;
        return result.get();
    }

    /**
     * Returns all components of the given type in the component tree, including root.
     */
    public static <T extends Component> List<T> findAll(Component root, Class<T> type) {
        List<T> result = new ArrayList<>();
        collect(root, type, false, result);
        return result;
    }

    /**
     * Like {@link #findAll(Component, Class)}, but skips invisible components and their children, e.g., cards of a
     * CardLayout that are not shown.
     */
    public static <T extends Component> List<T> findVisible(Component root, Class<T> type) {
        List<T> result = new ArrayList<>();
        collect(root, type, true, result);
        return result;
    }

    private static <T extends Component> void collect(Component c, Class<T> type, boolean visibleOnly, List<T> result) {
        if (visibleOnly && !c.isVisible()) return;
        if (type.isInstance(c)) result.add(type.cast(c));
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                collect(child, type, visibleOnly, result);
            }
        }
    }

    /**
     * Sizes the component and lays out its children. Swing does this only for components in a visible window.
     */
    public static void layout(Component component, int width, int height) {
        component.setSize(width, height);
        layout(component);
    }

    private static void layout(Component component) {
        if (component instanceof Container) {
            Container container = (Container) component;
            container.doLayout();
            for (Component child : container.getComponents()) {
                layout(child);
            }
        }
    }

    public static List<String> columnNames(JTable table) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < table.getColumnCount(); i++) {
            names.add(table.getColumnName(i));
        }
        return names;
    }

    /**
     * @return paths to all nodes of the tree, depth-first, excluding the root
     */
    public static List<TreePath> allPaths(JTree tree) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        List<TreePath> paths = new ArrayList<>();
        Enumeration<?> nodes = root.preorderEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            if (!node.isRoot()) paths.add(new TreePath(node.getPath()));
        }
        return paths;
    }

    /**
     * @return labels of all nodes of the tree, excluding the root
     */
    public static List<String> treeLabels(JTree tree) {
        return allPaths(tree).stream().map(p -> p.getLastPathComponent().toString()).collect(Collectors.toList());
    }

    /**
     * @return path to the node with the given label, or null if there is none
     */
    public static TreePath findPath(JTree tree, String label) {
        return allPaths(tree).stream()
                .filter(p -> p.getLastPathComponent().toString().equals(label))
                .findFirst().orElse(null);
    }
}
