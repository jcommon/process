package jcommon.process;

import java.util.Iterator;
import java.util.Map;
import java.util.LinkedHashMap;

public class EnvironmentVariableBlockBuilder {
  private LinkedHashMap<String, String> vars;

  public EnvironmentVariableBlockBuilder() {
  }

  @SuppressWarnings("unchecked")
  public EnvironmentVariableBlockBuilder copy() {
    final EnvironmentVariableBlockBuilder builder = new EnvironmentVariableBlockBuilder();
    synchronized (this) {
      if (vars != null) {
        builder.vars = (LinkedHashMap<String, String>)vars.clone();
      }
    }
    return builder;
  }

  /**
   * Creates an iterator that dynamically converts key/value pairs from a map into
   * {@link IEnvironmentVariable} instances.
   */
  private Iterator<IEnvironmentVariable> produceIteratorForMap(Map<String, String> map) {
    final Iterator<Map.Entry<String, String>> iter = map.entrySet().iterator();
    return new Iterator<IEnvironmentVariable>() {
      @Override
      public boolean hasNext() {
        return iter.hasNext();
      }

      @Override
      public IEnvironmentVariable next() {
        Map.Entry<String, String> e = iter.next();
        return new EnvironmentVariable(e.getKey(), e.getValue());
      }

      @Override
      public void remove() {
        iter.remove();
      }
    };
  }

  public void clear() {
    synchronized (this) {
      if (vars != null)
        vars.clear();
    }
  }

  public void addEnvironmentVariable(String name, String value) {
    assert name != null;
    assert value != null;

    synchronized (this) {
      if (vars == null) {
        vars = new LinkedHashMap<String, String>(2, 1.0f);
      }
    }

    vars.put(name, value);
  }

  public void removeEnvironmentVariable(String name) {
    synchronized (this) {
      if (vars == null)
        return;
      vars.remove(name);
    }
  }

  @SuppressWarnings("unchecked")
  public IEnvironmentVariableBlock toEnvironmentVariableBlock() {
    Map<String, String> mine;
    synchronized (this) {
      if (vars != null) {
        mine = (LinkedHashMap<String, String>)vars.clone();
      } else {
        mine = new LinkedHashMap<String, String>(0, 1.0f);
      }
    }

    final Map<String, String> my_vars = mine;

    return new IEnvironmentVariableBlock() {
      @Override
      public String get(String name) {
        return my_vars.get(name);
      }

      @Override
      public boolean contains(String name) {
        return my_vars.containsKey(name);
      }

      @Override
      public void enumerateVariables(IVisitor visitor) {
        for(Map.Entry<String, String> e : my_vars.entrySet()) {
          if (!visitor.visit(new EnvironmentVariable(e.getKey(), e.getValue()))) {
            return;
          }
        }
      }

      @Override
      public Iterator<IEnvironmentVariable> iterator() {
        return produceIteratorForMap(my_vars);
      }
    };
  }

  /**
   * Combines an existing block with the values in this {@link EnvironmentVariableBlockBuilder}
   * instance, preferring this instance's values over the provided {@link IEnvironmentVariableBlock}.
   *
   * @param block An instance of {@link IEnvironmentVariableBlock} with which the values will be combined.
   * @param visitor An instance of {@link IVisitor} that the caller provides for viewing the environment values this instance contains.
   */
  public void coalescedView(final IEnvironmentVariableBlock block, final IVisitor visitor) {
    Map<String, String> mine;
    synchronized (this) {
      if (vars != null) {
        mine = vars;
      } else {
        mine = null;
      }
    }

    final Map<String, String> my_vars = mine;

    //Iterate over all the provided block's entries, substituting my own
    //values when there's a matching key.
    block.enumerateVariables(new IEnvironmentVariableBlock.Visitor() {
      @Override
      public void visitEnvVars(IEnvironmentVariable var) {
        final String name = var.getName();
        final boolean result;

        //If I have the same entry, then use mine.
        //Otherwise, use the provided block's value.
        if (my_vars != null && my_vars.containsKey(name)) {
          result = visitor.visit(name, my_vars.get(name));
        } else {
          result = visitor.visit(name, var.getValue());
        }

        if (!result)
          return;
      }
    });

    //Now iterate over the keys in my own list that aren't in the block.
    if (my_vars != null) {
      for(Map.Entry<String, String> e : my_vars.entrySet()) {
        if (!block.contains(e.getKey())) {
          if (!visitor.visit(e.getKey(), e.getValue())) {
            return;
          }
        }
      }
    }
  }

  public IEnvironmentVariableBlock toCoalescedEnvironmentVariableBlock(final IEnvironmentVariableBlock block) {
    final Map<String, String> my_vars = new LinkedHashMap<String, String>(10, 8.0f);

    coalescedView(block, new IVisitor() {
      @Override
      public boolean visit(String name, String value) {
        my_vars.put(name, value);
        return true;
      }
    });

    return new IEnvironmentVariableBlock() {
      @Override
      public String get(String name) {
        return my_vars.get(name);
      }

      @Override
      public boolean contains(String name) {
        return my_vars.containsKey(name);
      }

      @Override
      public void enumerateVariables(IVisitor visitor) {
        for(Map.Entry<String, String> e : my_vars.entrySet()) {
          if (!visitor.visit(new EnvironmentVariable(e.getKey(), e.getValue()))) {
            return;
          }
        }
      }

      @Override
      public Iterator<IEnvironmentVariable> iterator() {
        return produceIteratorForMap(my_vars);
      }
    };
  }

  public static interface IVisitor {
    boolean visit(String name, String value);
  }
}
