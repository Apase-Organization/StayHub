"""Isolated stdlib checks: python3 scripts/demo_reset_smoke.py."""
from pathlib import Path
import unittest


class AmenitiesSmoke(unittest.TestCase):
    def test_names_remain_accessible(self):
        html = Path('src/main/resources/templates/property/property-detail.html').read_text()
        self.assertIn('th:text="${amenity.name', html)

    def test_raw_icon_keys_cannot_render_and_long_names_wrap(self):
        html = Path('src/main/resources/templates/property/property-detail.html').read_text()
        section = html.split('th:each="amenity :')[1].split('</section>')[0]
        self.assertNotIn('amenity.icon', section)
        self.assertIn('min-w-0', section)
        self.assertIn('break-words', section)

    def test_reset_report_is_read_only_and_rolls_back(self):
        sql = Path('scripts/prepare-demo-bookings.sql').read_text()
        self.assertIn('READ ONLY;', sql)
        self.assertTrue(sql.rstrip().endswith('ROLLBACK;'))
        self.assertIn("'PENDING','PENDING_PAYMENT','CONFIRMED'", sql)
        self.assertIn('EXCLUDED_EXECUTION_NOT_IMPLEMENTED', sql)


if __name__ == '__main__':
    unittest.main()
