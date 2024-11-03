JAVA = java
JAVAC = javac
LIB = lib
SRC = .
OUT = .
CP = $(LIB)/*:$(OUT)/
MAIN_SOURCES = $(wildcard *.java)
TEST_SOURCES = $(wildcard *_Test.java)
AGGREGATION_SERVER = AggregationServer
CONTENT_SERVER = ContentServer
CLIENT = GETClient
MAIN_SERVER = MainAggregationServer
MAIN = Main

all: compile-all

compile-all:
	@$(JAVAC) -cp $(CP) $(MAIN_SOURCES) $(TEST_SOURCES)

main: all
	@$(JAVA) -cp $(CP) $(MAIN_SERVER)

contentserver: all
	@$(JAVA) -cp $(CP) $(CONTENT_SERVER) localhost 4567 $(SRC)/data1_1.txt

client: all
	@$(JAVA) -cp $(CP) $(CLIENT) http://localhost:4567 IDS60901

test: all
	@$(JAVA) -cp $(CP) org.junit.platform.console.ConsoleLauncher --scan-classpath
