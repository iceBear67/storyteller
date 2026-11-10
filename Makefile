STC := toolchain/stc/stc
MINECRAFT ?= run
WORKSPACE ?= testpack
WORLD ?= $(shell ls -1 "$(MINECRAFT)/saves" 2>/dev/null | head -n 1)

.PHONY: watch stc

watch: stc
	@test -n "$(WORLD)" || { echo "no world found under $(MINECRAFT)/saves" >&2; exit 1; }
	$(STC) build --watch --minecraft "$(MINECRAFT)" --world "$(WORLD)" "$(WORKSPACE)"

stc:
	cd toolchain/stc && go build -o stc .
