# RDA tools moved

Pack preparation and catalog publishing live in the dedicated repository:

**https://github.com/cure76/ft8cn-rda-packs** → [`tools/`](https://github.com/cure76/ft8cn-rda-packs/tree/main/tools)

```bash
git clone git@github.com:cure76/ft8cn-rda-packs.git
cd ft8cn-rda-packs/tools
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python prepare_sm_smolensk.py
cp out/sm_smolensk.geojson ../packs/
python publish_catalog.py
```

The Android app only **consumes** packs (download / builtin assets).  
Optional mirror of published files in this repo: [`rda-packs/`](../../rda-packs/).
