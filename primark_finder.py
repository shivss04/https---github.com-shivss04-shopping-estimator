from bs4 import BeautifulSoup
import requests
import json
from selenium import webdriver
from selenium.webdriver.chrome.service import Service as ChromeService
import time
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.by import By
from selenium.webdriver.common.action_chains import ActionChains

# Configuration
CHROMEDRIVER_PATH = 'C:/Users/akhil/Desktop/chromedriver-win64/chromedriver.exe'
BASE_URL = 'https://www.primark.com/en-us/search'
SEARCH_TERM = 'womens joggers'
ZIP_CODE = '22102'
STORE_NAME = 'Tysons Corner Center, VA'

def initialize_driver():
    """
    Initializes the Chrome WebDriver.
    """
    service = ChromeService(executable_path=CHROMEDRIVER_PATH)
    driver = webdriver.Chrome(service=service)
    return driver

def accept_cookies(driver):
    """
    Accepts cookies on the Primark website.
    """
    try:
        wait = WebDriverWait(driver, 10)
        cookie_banner = wait.until(
            EC.presence_of_element_located((By.CLASS_NAME, "ot-sdk-container"))
        )
        accept_button = wait.until(
            EC.element_to_be_clickable((By.XPATH, "//button[contains(text(), 'Accept All Cookies')]"))
        )
        accept_button.click()
        print("Accepted cookies.")
        return True
    except Exception as e:
        print(f"Accept cookies didn't work: {e}")
        return False

def filter_by_store(driver, zip_code, store_name):
    """
    Filters the search results by store location.
    """
    try:
        wait = WebDriverWait(driver, 20)
        filter_by_store_label = wait.until(
            EC.element_to_be_clickable((By.XPATH, "//label[contains(., 'Filter by store')]"))
        )
        filter_by_store_label.click()
        print("Clicked 'Filter by store'.")
        time.sleep(1)

        zip_code_input = wait.until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "input[aria-labelledby='store-search-description'].MuiInputBase-input"))
        )
        zip_code_input.send_keys(zip_code)
        print(f"Entered '{zip_code}' in the zip code search.")
        time.sleep(2)

        search_button = wait.until(
            EC.element_to_be_clickable((By.XPATH, "//button[contains(text(), 'McLean, VA')]"))
        )
        search_button.click()
        print("Clicked the search button.")
        time.sleep(2)

        tysons_corner_label = wait.until(
            EC.element_to_be_clickable((By.XPATH, f"//label[contains(., '{store_name}')]"))
        )
        tysons_corner_label.click()
        print(f"Selected '{store_name}'.")
        time.sleep(1)

        accept_location_button = wait.until(
            EC.element_to_be_clickable((By.XPATH, "//button[contains(text(), 'SELECT STORE')]"))
        )
        accept_location_button.click()
        print("Clicked 'Select Store'.")
        time.sleep(2)
        return True
    except Exception as e:
        print(f"Filter by store didn't work: {e}")
        return False

def extract_product_details(html_page):
    """
    Extracts the name and price of the first product from the HTML.
    """
    soup = BeautifulSoup(html_page, 'html.parser')
    product_name = None
    product_price = None

    name_element = soup.find('h2', class_='ProductCard-title')
    product_name = name_element.find('a').text.strip() if name_element and name_element.find('a') else None

    price_element = soup.find('p', class_='ProductCard-price')
    product_price = price_element.find('a').text.strip() if price_element and price_element.find('a') else None

    return product_name, product_price

def main():
    """
    Main function to search for a product on Primark and extract details.
    """
    driver = initialize_driver()
    url_product = SEARCH_TERM.replace(' ', '+')
    url = f'{BASE_URL}?q={url_product}&tab=products'

    try:
        driver.get(url)

        accept_cookies(driver)
        filter_by_store(driver, ZIP_CODE, STORE_NAME)

        html_page = driver.page_source
        product_name, product_price = extract_product_details(html_page)

        print(f"Product Name: {product_name}")
        print(f"Product Price: {product_price}")

        time.sleep(5) 

    except Exception as e:
        print(f'Error during execution: {e}')

    finally:
        driver.quit()

if __name__ == "__main__":
    main()