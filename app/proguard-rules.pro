# Deliberately empty, and verified rather than assumed.
#
# Room, kotlinx-serialization and OkHttp each ship their own consumer ProGuard rules inside their
# artifacts, so R8 already keeps what they need. The release build was assembled with minify and
# resource shrinking on, signed, installed, and exercised on device through the paths that would
# break first if a rule were missing:
#
#   - Room            import wrote and read rows back
#   - kotlinx-serialization
#                     live ECB rates applied (INR 749 priced at the live rate, not the bundled
#                     table), and a backup file written through the generated serializers
#   - Compose         every screen rendered
#
# Add rules here only for something that actually breaks, with a note saying what and why. A
# speculative `-keep` is a class R8 can never shrink again.
