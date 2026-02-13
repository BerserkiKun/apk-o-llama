package analyzers;

import models.Finding;
import java.io.File;
import java.util.List;

public interface Analyzer {
    List<Finding> analyze(File file) throws Exception;
    String getAnalyzerName();
}
