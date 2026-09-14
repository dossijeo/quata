import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("inventory", Path(__file__).with_name("migration-statement-inventory.py"))
inventory = importlib.util.module_from_spec(spec)
spec.loader.exec_module(inventory)


class StatementInventoryTests(unittest.TestCase):
    def test_procedural_and_literal_semicolons_are_not_statement_boundaries(self):
        sql = "DO $$ BEGIN PERFORM 'a;b'; END $$; SELECT 'c;d';"
        result = inventory.inventory_sql(sql)
        self.assertEqual([s["kind"] for s in result], ["DoStmt", "SelectStmt"])
        self.assertIn("procedural", result[0]["reviewNeeds"][0])

    def test_offsets_use_utf8_bytes_with_non_ascii_comments(self):
        sql = "-- Qüata\nSELECT 'ñ';\nDELETE FROM public.example;"
        result = inventory.inventory_sql(sql)
        tail = sql.encode("utf-8")[result[1]["startByte"]:result[1]["endByte"]]
        self.assertIn(b"DELETE FROM public.example", tail)
        self.assertEqual(result[1]["kind"], "DeleteStmt")
        self.assertIn("data effects", result[1]["reviewNeeds"][0])

    def test_invalid_sql_fails_instead_of_producing_partial_inventory(self):
        with self.assertRaises(Exception):
            inventory.inventory_sql("SELECT 1; CREATE TABLE (")


if __name__ == "__main__":
    unittest.main()
