# TreeFeller Feature Implementation Plan

## 1. Feature Overview and Scope

### 1.1 Purpose
TreeFeller is a comprehensive tree-felling feature that allows players to cut down entire trees by breaking a single log block. The feature detects connected log blocks using BFS traversal, validates the structure with leaf detection, and breaks all connected wood blocks while respecting tool durability and configuration constraints.

### 1.2 Goals
- **Full tree detection**: Detect entire trees by traversing connected log blocks using BFS algorithm
- **Leaf validation**: Ensure detected structures are actual trees by validating leaf presence
- **Configurable tools**: Support custom tool configurations (material, durability, enchantments)
- **Configurable trees**: Support custom tree configurations (trunk blocks, leaf blocks, max height)
- **Shift-activation**: Use ActiveToolAPI pattern (10 shifts in 3 seconds) like OreExcavation and SandExcavation
- **Sound effects**: Configurable audio feedback
- **Sapling replanting**: Optional automatic sapling replanting
- **Falling tree animation**: Optional animated tree falling (configurable)
- **Debug mode**: Troubleshooting support for configuration issues

### 1.3 Non-Goals (Out of Scope for Initial Implementation)
- Plugin compatibility hooks (WorldGuard, GriefPrevention, McMMO, etc.) - can be added later
- In-game configuration GUI
- Economy integration
- Custom tree generation

---

## 2. File Structure and Classes

### 2.1 Package Structure
```
src/main/java/com/nyarutoru/nekoplugin/features/treefeller/
├── TreeFellerFeature.java          # Main feature class (extends AbstractFeature)
├── TreeFellerListener.java         # Event listener (implements Listener)
├── TreeFellerConfig.java           # Hardcoded configuration values
├── tree/
│   ├── TreeDetector.java           # BFS-based tree detection algorithm
│   ├── TreeStructure.java          # Represents a detected tree structure
│   ├── TreeType.java               # Tree type definition
│   └── LeafValidator.java          # Validates leaf presence for tree detection
├── tool/
│   ├── ToolMatcher.java            # Matches items against configured tools
│   └── ToolConfig.java             # Tool definition
└── animation/
    ├── FallingTreeAnimation.java   # Optional falling tree animation
    └── TreeFellerEffects.java      # Particle and sound effects
```

### 2.2 Resource Files
```
src/main/resources/
└── paper-plugin.yml                # No commands needed (uses ActiveToolAPI)
```

**Note**: All configuration values are hardcoded in TreeFellerConfig.java based on reference implementation defaults. No YAML configuration files are used.

### 2.3 Class Responsibilities

#### TreeFellerFeature.java
- Extends `AbstractFeature` with id="treefeller", name="TreeFeller"
- Initializes configuration on enable
- Registers TreeFellerListener
- Logs startup status

#### TreeFellerListener.java
- Implements `Listener` for event handling
- Uses ActiveToolAPI for shift-activation (10 shifts within 3 seconds)
- Handles `PlayerToggleSneakEvent` for tool activation
- Handles `BlockBreakEvent` for tree felling logic
- Implements tree detection and validation
- Manages effects and animations
- Handles tool durability consumption
- Supports debug messaging

#### TreeFellerConfig.java
- Contains hardcoded configuration values based on reference implementation defaults
- Provides static accessor methods for configuration values
- No reload functionality (requires server restart to change values)
- Configuration sections:
  - Global settings (enabled, debug, max-tree-size, etc.)
  - Tool configurations (material, enchantments, durability cost)
  - Tree configurations (log blocks, leaf blocks, validation rules)
  - Sound effects settings
  - Animation settings (falling tree enabled, speed)

#### TreeDetector.java
- Implements BFS algorithm for tree detection
- Starts from broken log block
- Traverses connected log blocks (6-directional by default, configurable for diagonal)
- Respects max-tree-size limit
- Returns `TreeStructure` object with all detected blocks
- Uses `BlockPos` for efficient position tracking

#### TreeStructure.java
- Record class holding detected tree data
- Fields: `List<BlockPos> logs`, `List<BlockPos> leaves`, `BlockPos origin`, `TreeType treeType`
- Provides methods for block counting, location conversion

#### TreeType.java
- Represents a configured tree type
- Fields: `name`, `logBlocks`, `leafBlocks`, `maxHeight`, `requiredLeaves`, `leafDetectRange`, `leafBreakRange`, `diagonalLogs`, `ignoreLeafData`, `allowPlayerPlaced`
- Provides matching logic against detected structures

#### LeafValidator.java
- Validates that detected structure is a valid tree
- Checks leaf presence within configured range
- Validates leaf count meets minimum requirement
- Supports configurable leaf detection patterns

#### ToolMatcher.java
- Matches player's held item against configured tools
- Checks material type
- Checks required enchantments (type and level)
- Checks forbidden enchantments
- Checks item name/lore (optional)
- Checks allowed trees for this tool
- Returns matched `ToolConfig` or null

#### ToolConfig.java
- Represents a configured tool
- Fields: `name`, `material`, `requiredEnchantments`, `forbiddenEnchantments`, `durabilityCost`, `allowedTrees`, `worlds`, `gameModes`, `timeRange`
- Provides matching logic and validation

#### FallingTreeAnimation.java
- Optional animated tree falling effect
- Uses `BukkitScheduler` for sequential block breaking
- Breaks blocks from bottom to top with delay
- Configurable animation speed
- Can be disabled for instant breaking

#### TreeFellerEffects.java
- Plays sound effects at break locations
- Configurable sound types and volume

---

## 3. Hardcoded Configuration Values

All configuration values are hardcoded in `TreeFellerConfig.java` based on reference implementation defaults. To modify these values, edit the source code directly.

### 3.1 Global Settings
- `ENABLED = true` - Enable/disable the entire feature
- `DEBUG = false` - Debug mode for troubleshooting
- `MAX_TREE_SIZE = 500` - Maximum blocks that can be felled at once
- `REQUIRE_LEAVES = true` - Require leaves for tree detection
- `MINIMUM_LEAVES = 10` - Minimum leaf count required
- `LEAF_DETECT_RANGE = 5` - Range to search for leaves
- `LEAF_BREAK_RANGE = 3` - Range to break leaves
- `DIAGONAL_LOGS = false` - Allow diagonal log connections
- `IGNORE_LEAF_DATA = false` - Ignore leaf block data
- `ALLOW_PLAYER_PLACED = true` - Allow player-placed trees
- `REPLANT_SAPLINGS = false` - Replant saplings after felling
- `REPLANT_CHANCE = 1.0` - Sapling replant chance (0.0-1.0)

### 3.2 Sound Effects Settings
- `SOUNDS_ENABLED = true` - Enable sound effects
- `FELL_SOUND = Sound.BLOCK_WOOD_BREAK` - Sound when tree is felled
- `SOUND_VOLUME = 1.0f` - Sound volume
- `SOUND_PITCH = 1.0f` - Sound pitch

### 3.3 Animation Settings
- `ANIMATION_ENABLED = false` - Enable falling tree animation
- `ANIMATION_DELAY_TICKS = 2` - Delay between blocks breaking
- `ANIMATION_BOTTOM_UP = true` - Break from bottom to top

### 3.5 Tool Configurations
All axe types are enabled by default with the following settings:
- **Iron Axe**: Material.IRON_AXE, durabilityCost=1, no enchantment requirements
- **Diamond Axe**: Material.DIAMOND_AXE, durabilityCost=2, no enchantment requirements
- **Netherite Axe**: Material.NETHERITE_AXE, durabilityCost=3, no enchantment requirements
- **Golden Axe**: Material.GOLDEN_AXE, durabilityCost=1, no enchantment requirements
- **Wooden Axe**: Material.WOODEN_AXE, durabilityCost=1, no enchantment requirements
- **Stone Axe**: Material.STONE_AXE, durabilityCost=1, no enchantment requirements

All tools work in SURVIVAL and ADVENTURE game modes, in all worlds, with all tree types.

### 3.6 Tree Type Configurations
All vanilla wood types are configured with their corresponding log and leaf blocks:
- **Oak**: OAK_LOG, OAK_LEAVES, maxHeight=50, requiredLeaves=10
- **Spruce**: SPRUCE_LOG, SPRUCE_LEAVES, maxHeight=80, requiredLeaves=15
- **Birch**: BIRCH_LOG, BIRCH_LEAVES, maxHeight=50, requiredLeaves=10
- **Jungle**: JUNGLE_LOG, JUNGLE_LEAVES, maxHeight=100, requiredLeaves=20
- **Acacia**: ACACIA_LOG, ACACIA_LEAVES, maxHeight=50, requiredLeaves=10
- **Dark Oak**: DARK_OAK_LOG, DARK_OAK_LEAVES, maxHeight=50, requiredLeaves=15
- **Mangrove**: MANGROVE_LOG, MANGROVE_LEAVES, maxHeight=60, requiredLeaves=15
- **Cherry**: CHERRY_LOG, CHERRY_LEAVES, maxHeight=50, requiredLeaves=10

All tree types use leafDetectRange=5, leafBreakRange=3, diagonalLogs=false, ignoreLeafData=false, allowPlayerPlaced=true.

---

## 4. Command Structure

### 4.1 Command Registration (paper-plugin.yml)
```yaml
commands:
  treefeller:
    description: "TreeFeller feature commands"
    usage: "/treefeller <reload|help|toggle|on|off|debug>"
    permission: "treefeller.use"
    aliases: ["tf", "tree"]
```

### 4.2 Permissions (paper-plugin.yml)
```yaml
permissions:
  treefeller.use:
    description: "Allows using tree feller feature"
    default: true
  treefeller.reload:
    description: "Allows reloading tree feller configuration"
    default: op
  treefeller.help:
    description: "Allows viewing tree feller help"
    default: true
  treefeller.toggle:
    description: "Allows toggling tree feller on/off"
    default: true
  treefeller.debug:
    description: "Allows toggling debug mode"
    default: op
  treefeller.bypass.tool-check:
    description: "Bypasses tool requirement checks"
    default: op
```

### 4.3 Command Help Messages
```
=== TreeFeller Commands ===
/treefeller help - Show this help message
/treefeller toggle - Toggle tree feller on/off
/treefeller on - Enable tree feller
/treefeller off - Disable tree feller
/treefeller reload - Reload configuration (admin only)
/treefeller debug [on|off] - Toggle debug mode (admin only)

Current Status: [ENABLED/DISABLED]
```

---

## 5. Implementation Sequence

### Phase 1: Core Infrastructure (Days 1-2)
**Goal**: Establish basic feature structure and hardcoded configuration

1. **Create directory structure**
   - Create all package directories under `features/treefeller/`

2. **Create configuration class**
   - `TreeFellerConfig.java` - Hardcoded configuration values from reference implementation
   - Include all global settings, tool configs, tree configs, effects, animation settings
   - No YAML parsing or file loading required

3. **Create basic feature class**
   - `TreeFellerFeature.java` - Extend AbstractFeature
   - Implement onEnable/onDisable lifecycle
   - Register with FeatureManager

**Testing Checkpoint**: Feature loads successfully, configuration values are accessible, state management works

### Phase 2: Tree Detection Algorithm (Days 3-4)
**Goal**: Implement BFS-based tree detection with leaf validation

1. **Create tree structure classes**
   - `TreeStructure.java` - Record for detected tree data
   - `TreeType.java` - Configured tree type definition

2. **Implement leaf validator**
   - `LeafValidator.java` - Validate leaf presence and count
   - Support configurable leaf detection range
   - Support ignore-leaf-data option

3. **Implement tree detector**
   - `TreeDetector.java` - BFS traversal algorithm
   - Use `BlockPos` for efficient position tracking
   - Support 6-directional and diagonal traversal
   - Respect max-tree-size limit
   - Return complete `TreeStructure`

4. **Unit tests**
   - Test BFS algorithm with various tree shapes
   - Test leaf validation with different configurations
   - Test edge cases (single log, no leaves, etc.)

**Testing Checkpoint**: Tree detection correctly identifies vanilla trees, leaf validation works

### Phase 3: Tool System and Event Handling (Days 5-6)
**Goal**: Implement tool matching and event listeners

1. **Create tool configuration classes**
   - `ToolConfig.java` - Tool definition with enchantment requirements
   - `ToolMatcher.java` - Match items against configured tools

2. **Create event listener**
   - `TreeFellerListener.java` - Main event handler
   - Implement `PlayerToggleSneakEvent` for ActiveToolAPI integration
   - Implement `BlockBreakEvent` for tree felling logic
   - Implement tool validation on block break
   - Handle player events (join, quit, death, teleport, item switch)

3. **Integrate with ActiveToolAPI**
   - Use shift-activation pattern (10 shifts in 3 seconds)
   - Display action bar during activation
   - Handle deactivation on tool switch/break

4. **Implement tree felling logic**
   - Break detected log blocks
   - Break detected leaf blocks (within range)
   - Apply durability cost
   - Handle tool breaking mid-operation

**Testing Checkpoint**: Tool activation works, trees are felled correctly, durability is consumed

### Phase 4: Commands and Permissions (Day 7)
**Goal**: Implement command system with permissions

1. **Update paper-plugin.yml**
   - Add command definitions
   - Add permission definitions

2. **Create command handler**
   - `TreeFellerCommands.java` - CommandExecutor and TabCompleter
   - Implement all subcommands (reload, help, toggle, on, off, debug)
   - Implement tab completion

3. **Implement permission checks**
   - Check permissions before command execution
   - Provide appropriate error messages

4. **Create help messages**
   - Use ComponentUtils for formatted messages
   - Include current status in help output

**Testing Checkpoint**: All commands work, permissions are enforced, tab completion works

### Phase 5: Effects and Animation (Day 8)
**Goal**: Add audio feedback and optional animation

1. **Create effects system**
   - `TreeFellerEffects.java` - Sound effects
   - Implement configurable sounds
   - Support volume and pitch control

2. **Create animation system** (optional)
   - `FallingTreeAnimation.java` - Sequential block breaking
   - Implement bottom-up breaking order
   - Configurable delay between blocks
   - Support instant breaking (animation disabled)

3. **Integrate with tree felling**
   - Play effects when tree is felled
   - Apply animation if enabled
   - Handle animation cancellation

**Testing Checkpoint**: Effects play correctly, animation works (if enabled)

### Phase 6: Advanced Features (Days 9-10)
**Goal**: Implement sapling replanting and polish

1. **Implement sapling replanting**
   - Add replant logic after tree felling
   - Support configurable replant chance
   - Match sapling type to tree type
   - Check for valid planting location

2. **Implement debug mode**
   - Add detailed logging in debug mode
   - Show tree detection information
   - Show tool matching information

3. **Configuration hot-reload** (removed - no commands)
   - Not applicable (hardcoded configuration)

4. **Error handling and edge cases**
   - Handle null safety throughout
   - Handle world unload scenarios
   - Handle player disconnect during felling
   - Handle concurrent tree felling attempts

**Testing Checkpoint**: All features work, debug mode provides useful information, reload works

### Phase 7: Testing and Documentation (Day 11)
**Goal**: Comprehensive testing and documentation

1. **Integration testing**
   - Test all tree types in survival mode
   - Test tool requirements and durability
   - Test edge cases (large trees, custom trees)

2. **Performance testing**
   - Test with max-tree-size limit
   - Test with multiple players felling simultaneously
   - Monitor server TPS during tree felling
   - Optimize BFS algorithm if needed

3. **Documentation**
   - Add JavaDoc to all public classes and methods
   - Create configuration guide in comments
   - Document known limitations

4. **Bug fixes and polish**
   - Fix any issues found during testing
   - Optimize performance bottlenecks
   - Clean up code and remove debug statements

**Testing Checkpoint**: All tests pass, performance is acceptable, code is documented

---

## 6. API Dependencies

### 6.1 Paper API Usage
- **BlockBreakEvent**: Primary event for tree felling trigger
- **PlayerToggleSneakEvent**: For ActiveToolAPI activation
- **BukkitScheduler**: For animation and delayed tasks
- **World.getBlockAt()**: For block access during BFS
- **Block.breakNaturally()**: For breaking blocks with proper drops
- **Block.getDrops()**: For getting block drops
- **World.dropItemNaturally()**: For dropping items
- **World.playSound()**: For sound effects
- **Player.getAttribute()**: For potential future enhancements
- **RegistryAccess**: For material lookup if needed

### 6.2 Existing NekoPlugin APIs
- **AbstractFeature**: Base class for feature lifecycle
- **ActiveToolAPI**: Shift-activation system
- **BlockPos**: Efficient block position tracking
- **ItemUtils**: Durability handling utilities
- **ComponentUtils**: Adventure API text formatting
- **SchedulerUtils**: Safe task scheduling
- **FeatureManager**: Feature registration and management

### 6.3 Third-Party Libraries
- **Adventure API** (bundled with Paper): For player messages
  - `net.kyori.adventure.text.Component`
  - `net.kyori.adventure.text.format.NamedTextColor`
- **Bukkit Configuration API**: For YAML parsing
  - `org.bukkit.configuration.file.FileConfiguration`
  - `org.bukkit.configuration.file.YamlConfiguration`

### 6.4 Version-Specific Considerations (1.21.1)
- Use `io.papermc.paper.registry.RegistryAccess` for registry lookups
- Use Adventure API for all text (no legacy color codes)
- Use `Block.breakNaturally(ItemStack, boolean)` for silk touch support
- Material names are stable for wood types (OAK_LOG, OAK_LEAVES, etc.)

---

## 7. Design Patterns

### 7.1 Strategy Pattern
- **ToolMatcher**: Different tool matching strategies (by material, enchantments, etc.)
- **TreeDetector**: Different detection strategies (6-directional vs diagonal)

### 7.2 Factory Pattern
- **TreeType**: Factory method for creating tree types from configuration

### 7.3 Singleton Pattern
- **TreeFellerConfig**: Single configuration instance

### 7.4 Observer Pattern
- **TreeFellerListener**: Observes Bukkit events and reacts accordingly

---

## 8. State Management Approach

### 8.1 Player State
- **Debug State**: Managed via TreeFellerConfig.DEBUG constant
- **No per-player state**: Feature is always active when holding valid tool

### 8.2 Configuration State
- **Hardcoded Values**: All configuration in TreeFellerConfig.java
- **Thread Safety**: Configuration is read-only (static final fields)

### 8.3 Tree Detection State
- **Per-Operation**: Tree detection is stateless between operations
- **Recursion Prevention**: `Set<BlockPos>` tracks blocks being broken to prevent recursion
- **Cleanup**: Recursion set is cleared after each operation

---

## 9. Success Criteria

### 9.1 Functional Requirements
- [ ] Feature loads and enables without errors
- [ ] Hardcoded configuration values are correctly applied
- [ ] Tree detection correctly identifies all vanilla tree types
- [ ] Leaf validation prevents felling of player-built structures
- [ ] Tool requirements are enforced (material, enchantments)
- [ ] Durability is consumed correctly (respecting Unbreaking)
- [ ] Shift-activation works (10 shifts within 3 seconds)
- [ ] Debug mode provides useful troubleshooting information
- [ ] Effects play correctly (sounds)
- [ ] Animation works when enabled (optional)
- [ ] Sapling replanting works when enabled (optional)
- [ ] Feature cleans up properly on disable

### 9.2 Performance Criteria
- [ ] Tree detection completes within 50ms for trees up to max-tree-size
- [ ] No significant TPS impact during tree felling
- [ ] Memory usage is reasonable (no memory leaks)
- [ ] BFS algorithm is optimized (uses BlockPos, not Location)

### 9.3 Edge Cases
- [ ] Single log block (no tree) - should not trigger
- [ ] Tree with no leaves - should not trigger (if require-leaves=true)
- [ ] Tree exceeding max-tree-size - should only fell up to limit
- [ ] Tool breaking mid-operation - should stop and deactivate
- [ ] Player disconnect during felling - should clean up properly
- [ ] World unload during felling - should handle gracefully
- [ ] Concurrent felling attempts - should handle thread-safely
- [ ] Custom trees with diagonal logs - should detect if enabled
- [ ] Player-placed trees - should respect allow-player-placed setting
- [ ] Mixed wood type trees - should detect if configured

### 9.4 Code Quality
- [ ] All public methods have JavaDoc
- [ ] Code follows existing NekoPlugin patterns
- [ ] Configuration values are centralized in TreeFellerConfig class
- [ ] Proper null safety throughout
- [ ] No use of System.out.println (use getLogger())
- [ ] Async-safe operations where applicable
- [ ] No blocking operations on main thread
- [ ] Unit tests for core algorithms (TreeDetector, LeafValidator)

---

## 10. Implementation Notes

### 10.1 BFS Algorithm Optimization
- Use `BlockPos` instead of `Location` to reduce object allocation
- Use `HashSet<BlockPos>` for visited tracking (optimized hashCode)
- Use `ArrayDeque<BlockPos>` for BFS queue
- Early radius checking to avoid unnecessary block lookups
- 6-directional by default (CARDINAL_OFFSETS), configurable for diagonal

### 10.2 Durability Handling
- Use existing `ItemUtils.consumeDurabilityOrDeactivate()` method
- Respect Unbreaking enchantment (already implemented in ItemUtils)
- Check for unbreakable items (creative mode, custom items)
- Deactivate ActiveToolAPI if tool would break

### 10.3 Leaf Detection Strategy
- Search for leaves within configured radius from trunk blocks
- Count unique leaf blocks (not total blocks)
- Support multiple leaf types per tree type
- Option to ignore leaf data (for custom trees with persistent leaves)

### 10.4 Silk Touch Handling
- If tool has Silk Touch, drop log blocks instead of saplings
- Use `Block.breakNaturally(tool, true)` for silk touch drops
- Use `Block.breakNaturally(tool)` for normal drops

### 10.5 Thread Safety
- All player state maps use `ConcurrentHashMap`
- Configuration is immutable (hardcoded constants)
- Event handlers run on main thread (Bukkit guarantee)
- Animation tasks scheduled via SchedulerUtils

### 10.6 Integration Points
- **ActiveToolAPI**: Use for shift-activation (10 shifts in 3 seconds)
- **FeatureManager**: Register feature in NekoPlugin.java
- **paper-plugin.yml**: Add commands and permissions
- **ItemUtils**: Use for durability handling
- **ComponentUtils**: Use for all player messages
- **SchedulerUtils**: Use for all task scheduling

### 10.7 Configuration Approach
- All configuration values are hardcoded in TreeFellerConfig.java
- No configuration files are loaded or saved
- Changes to configuration require code modification and recompilation
- This simplifies the implementation and reduces file I/O overhead

---

## 11. Testing Strategy

### 11.1 Unit Tests
- **TreeDetectorTest**: Test BFS algorithm with various tree shapes
- **LeafValidatorTest**: Test leaf validation logic
- **ToolMatcherTest**: Test tool matching with enchantments

### 11.2 Integration Tests
- **TreeFellingIntegrationTest**: Test full tree felling flow

### 11.3 Manual Testing Checklist
- [ ] Test all vanilla tree types (oak, spruce, birch, jungle, acacia, dark oak, mangrove, cherry)
- [ ] Test with different tool types (wood, stone, iron, gold, diamond, netherite)
- [ ] Test with different enchantments (Efficiency, Unbreaking, Silk Touch)
- [ ] Test shift-activation (10 shifts within 3 seconds)
- [ ] Test debug mode output
- [ ] Test effects (sounds)
- [ ] Test animation (if enabled)
- [ ] Test sapling replanting (if enabled)
- [ ] Test edge cases (single log, no leaves, large trees)

---

## 12. Future Enhancements (Out of Scope)

### 12.1 Plugin Compatibility Hooks
- WorldGuard region checking
- GriefPrevention claim checking
- Towny town/resident checking
- McMMO skill integration
- Jobs Reborn job integration
- CoreProtect logging
- MMOCore integration
- EcoSkills integration

### 12.2 Advanced Features
- In-game configuration GUI
- Economy integration (cost per tree)
- Experience orb rewards
- Custom tree generation support
- Axe leveling system
- Tree felling statistics
- Leaderboards

### 12.3 Performance Optimizations
- Async tree detection (if safe)
- Caching of tree structures
- Region-based tree pre-scanning
- Optimized leaf detection algorithms

---

## 13. Risk Assessment

### 13.1 Technical Risks
- **BFS Performance**: Large trees could cause lag
  - Mitigation: Max-tree-size limit, optimized BlockPos usage
- **Recursion**: Tree felling could trigger additional block breaks
  - Mitigation: Track breaking blocks in Set, skip already-breaking blocks
- **Thread Safety**: Concurrent player actions could cause issues
  - Mitigation: Use ConcurrentHashMap, main-thread event handling
- **Memory Leaks**: Player state not cleaned up on quit
  - Mitigation: Cleanup on PlayerQuitEvent, use WeakHashMap if needed

### 13.2 Configuration Risks
- **Invalid Configuration**: Hardcoded values could have typos or errors
  - Mitigation: Code review, compile-time validation, testing
- **Configuration Changes**: Requires code modification and recompilation
  - Mitigation: Clear documentation of all configuration values in TreeFellerConfig.java

### 13.3 Compatibility Risks
- **Other Plugins**: Could conflict with other tree-felling plugins
  - Mitigation: Document known conflicts, provide disable option
- **Custom Trees**: May not detect all custom tree types
  - Mitigation: Provide configuration for custom trees, diagonal-logs option
- **Version Changes**: Minecraft updates could break material names
  - Mitigation: Use Material enum, test on new versions

---

## 14. Acceptance Criteria Summary

The TreeFeller feature will be considered complete when:

1. ✅ All phases (1-7) are implemented and tested
2. ✅ All functional requirements are met (Section 9.1)
3. ✅ All performance criteria are met (Section 9.2)
4. ✅ All edge cases are handled (Section 9.3)
5. ✅ Code quality standards are met (Section 9.4)
6. ✅ Unit tests pass with >80% coverage on core algorithms
7. ✅ Integration tests pass for all major features
8. ✅ Manual testing checklist is completed
9. ✅ Documentation is complete (JavaDoc, configuration comments)
10. ✅ Feature is registered in NekoPlugin.java and enabled by default
11. ✅ No console errors during normal operation
12. ✅ No significant TPS impact during tree felling

---

## 15. Estimated Timeline

| Phase | Description | Estimated Time | Dependencies |
|-------|-------------|----------------|--------------|
| 1 | Core Infrastructure | 2 days | None |
| 2 | Tree Detection Algorithm | 2 days | Phase 1 |
| 3 | Tool System and Event Handling | 2 days | Phase 2 |
| 4 | Commands and Permissions | 1 day | Phase 3 |
| 5 | Effects and Animation | 1 day | Phase 3 |
| 6 | Advanced Features | 2 days | Phase 4, 5 |
| 7 | Testing and Documentation | 1 day | All phases |
| **Total** | | **11 days** | |

**Note**: Timeline assumes single developer working full-time. Actual timeline may vary based on complexity discovered during implementation and testing results.

---

## 16. Additional Research Conducted

During the planning process, the following research was conducted:

1. **Reference Implementation Review**: Analyzed the ThizThizzyDizzy/tree-feller repository to understand key features including:
- Custom tree configuration with log and leaf blocks
- Custom tool configuration with enchantments
- BFS-based tree detection
- Leaf validation system
- Shift-activation via ActiveToolAPI (10 shifts in 3 seconds)
- Effects (sounds)
- Falling tree animation
- Sapling replanting

2. **NekoPlugin Pattern Analysis**: Reviewed existing features to understand:
   - AbstractFeature lifecycle pattern
   - ActiveToolAPI integration for shift-activation
   - OreExcavation/SandExcavation activation patterns
   - Configuration patterns (currently hardcoded in *Config classes)
   - Command patterns (GraveCommands)
   - Utility classes (BlockPos, ItemUtils, ComponentUtils, SchedulerUtils)

3. **Paper 1.21.1 API Verification**: Confirmed API availability for:
   - BlockBreakEvent and related events
   - BukkitScheduler for async tasks
   - Adventure API for text formatting
   - RegistryAccess for material lookups
   - Block.breakNaturally() for proper block breaking

No additional API research was needed beyond what was provided in the project context, as the existing NekoPlugin codebase already demonstrates proper usage of Paper 1.21.1 APIs.

---

*This plan was created for NekoPlugin TreeFeller feature implementation. Last updated: 2026-03-21*
