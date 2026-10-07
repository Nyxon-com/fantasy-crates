// made by nyxon
package net.nyxon.crates.crate;
import java.util.List;
public record MilestoneDefinition(String id, int openingsRequired, List<String> commands, boolean broadcast, boolean resetAfterClaim) { }
