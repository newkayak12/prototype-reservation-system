#!/usr/bin/env bash
# statusline.sh — SessionStart hook: 모델·컨텍스트·사용량·브랜치 요약 출력

BRANCH=$(git -C "$CLAUDE_PROJECT_DIR" branch --show-current 2>/dev/null || echo "unknown")

# budget info from ~/.claude/budget.json
BUDGET_FILE="$HOME/.claude/budget.json"
BUDGET_5H="?" BUDGET_1W="?"
if [ -f "$BUDGET_FILE" ]; then
  read BUDGET_5H BUDGET_1W <<< $(python3 -c "
import json
d=json.load(open('$BUDGET_FILE'))
print(d.get('5h','?'), d.get('1w','?'))
" 2>/dev/null)
fi

# active cycle info
CYCLE_DIR="$CLAUDE_PROJECT_DIR/cycles/active"
CYCLE_ID="" SESSION="" APPETITE="" PHASE=""
if [ -L "$CYCLE_DIR" ] || [ -d "$CYCLE_DIR" ]; then
  METRICS="$CYCLE_DIR/metrics.json"
  if [ -f "$METRICS" ]; then
    read CYCLE_ID SESSION APPETITE PHASE <<< $(python3 -c "
import json
d=json.load(open('$METRICS'))
print(d.get('cycle_id','?'), d.get('session_count','?'), d.get('appetite_sessions','?'), d.get('current_phase','?'))
" 2>/dev/null)
  fi
fi

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🤖 Model: Claude Opus 4.6"
echo "📐 Context: (세션 시작 — 미측정)"
echo "⏱️  5h budget: \$$BUDGET_5H remaining"
echo "📅 1w budget: \$$BUDGET_1W remaining"
echo "🌿 Branch: $BRANCH"
if [ -n "$CYCLE_ID" ]; then
  echo "🔄 Cycle: $CYCLE_ID (phase: $PHASE)"
  echo "📊 Session: $SESSION (appetite: $APPETITE)"
fi
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
