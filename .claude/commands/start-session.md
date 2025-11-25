# Start App Session

## Overview
This command streamlines the start of a new development session for IronLog. It:
1. Greets the user with a fun message
2. Reviews the project state and gives 3 feature suggestions
3. Asks which feature to work on
4. Creates a descriptive feature branch
5. Reminds about PR workflow
6. Returns ready to code

## Implementation

When invoked, Claude should:

### Step 1: Fun Greeting
Output an energetic, brief greeting that acknowledges it's a new session. Keep it short (1-2 sentences). Use an emoji that fits the vibe.

Example variations:
- "Let's build something awesome! 💪 Time to make IronLog even better."
- "New session, new features! 🚀 What are we shipping today?"
- "Back to the grind! 💎 Let's make this app legendary."

### Step 2: Project Review & Suggestions
Read CLAUDE.md to understand:
- Current project status
- What's been completed
- What's in the roadmap

Based on the session_logs (especially the most recent one), review:
- What was just completed
- What are the top 3 priorities

Provide 3 concrete feature suggestions that:
1. Build on recent momentum
2. Provide clear user value
3. Are achievable in a single session (3-6 hours)
4. Follow the roadmap priorities

Format as numbered list with brief description of what will be built and why it matters.

### Step 3: Ask for Feature Selection
Ask the user which of the 3 options they'd like to work on, or if they have something else in mind.

Use AskUserQuestion tool with:
- The 3 suggestions as options
- "Other" option for custom feature
- Allow single selection

### Step 4: Create Feature Branch
Based on their selection:
1. Generate a descriptive branch name following conventions:
   - `feat/` for new features
   - `fix/` for bug fixes
   - `refactor/` for improvements
   - Format: `feat/feature-name` (kebab-case, concise)

2. Confirm the branch name with the user (show it to them)

3. Execute the git commands:
   ```bash
   git checkout -b [branch-name]
   ```

4. Verify the branch was created successfully

### Step 5: Remind About PR Workflow
Explain the workflow for this session:
- Make changes and commit regularly
- When done, push the branch
- Create a PR for review
- Merge after testing
- Work stays isolated until ready

Keep it brief (3-4 sentences) but clear.

### Step 6: Return Ready to Work
End with a clear "ready to work" statement and ask what they want to tackle first.

Format: "Branch created! Ready to build [feature-name]. What should we start with?"

## Important Notes

- Always read CLAUDE.md and the most recent session log for context
- Prioritize features from the "Next Session Priorities" section of recent session logs
- If the user picks "Other", ask them to clarify what they want to build
- Be enthusiastic but concise - don't write a novel
- Remember: the goal is to get them coding, not talking
- Use Bash tool to execute git commands (git checkout -b)
- Use AskUserQuestion tool for feature selection
- Use Explore agent to review project state if needed

## Example Flow

```
🚀 Let's ship something amazing! Back in the saddle?

I reviewed your recent work on Exercise History and the next priorities:

1. **History Screen** - Display past workouts with replay feature
   Why: Closes the feedback loop, leverages all the seed data you created

2. **Set Editing & Deletion** - Let users fix logging mistakes
   Why: Prevents bad data from corrupting PRs, respects user control

3. **Routine Builder** - Create and quick-start workout routines
   Why: Accelerates workflow, reduces friction for repeat workouts

Which would you like to tackle first? Or something else in mind?

[User picks: History Screen]

Perfect! Creating branch: feat/history-screen

Branch created and checked out! 🎯

Here's the workflow for today:
- Make changes, commit regularly to this branch
- When done, push the branch to GitHub
- Create a PR for review/testing
- Once tested, merge back to main

Let's go! What should we start with - the data layer (queries), the ViewModel, or the UI?
```

## Files to Reference

- `CLAUDE.md` - Project overview, completed features, roadmap
- `session_logs/` - Most recent session report for priorities
- Current git branch state

## Commands This Uses

- AskUserQuestion - For feature selection
- Bash - For git branch creation
- Read - For CLAUDE.md and session logs
- Explore agent - Optional, for understanding project state
