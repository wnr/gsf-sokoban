all: test

sample:
	@node test.js 0

full:
	@node test.js 12000 11

test:
	@node test.js 25 11

kattis:
	@node test.js test-kattis.data