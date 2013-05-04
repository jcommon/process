package jcommon.process;

public interface IEnvironmentVariableBlock extends Iterable<IEnvironmentVariable> {
  public static interface IVisitor {
    boolean visit(IEnvironmentVariable var);
  }

  public static abstract class Visitor implements IVisitor {
    public abstract void visitEnvVars(IEnvironmentVariable var);

    public boolean visit(IEnvironmentVariable var) {
      visitEnvVars(var);
      return true;
    }
  }

  String get(String name);
  boolean contains(String name);
  void enumerateVariables(IVisitor visitor);
}
