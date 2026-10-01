// made by haze
package it.haze.hazecrates.crate;
import java.util.List;
public record MilestoneDefinition(String id, int openingsRequired, List<String> commands, boolean broadcast, boolean resetAfterClaim) { }
