Generate NED YANG Models
========================

Generates new netsim example NED YANG models from production NEDs.

Generating YANG Models:
-----------------------

1. Copy installed and extracted production NED package(s) to the `ned-packages/`
   directory
2. Adjust version number(s) in the Makefile to match the production NED version
3. Requires BeautifulSoup4 and pyang. To install:

       pip install -r requirements.txt

4. Run `make help`for build help
